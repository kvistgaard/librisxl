package whelk.component

import com.google.common.collect.Iterators
import groovy.util.logging.Log4j2 as Log
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.prometheus.collectors.CircuitBreakerMetricsCollector
import io.github.resilience4j.prometheus.collectors.RetryMetricsCollector
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.prometheus.client.CollectorRegistry
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.PoolingClientConnectionManager
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams
import org.apache.http.util.EntityUtils
import whelk.exception.ElasticIOException

import java.time.Duration
import java.util.function.Function

@Log
class ElasticClient {
    static final int MAX_CONNECTIONS_PER_HOST = 12
    static final int CONNECTION_POOL_SIZE = 30

    static final int CONNECT_TIMEOUT_MS = 5 * 1000
    static final int READ_TIMEOUT_MS = 40 * 1000
    static final int MAX_BACKOFF_S = 1024

    static final CircuitBreakerConfig CB_CONFIG = CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(10)
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(1)
            .build()

    List<ElasticNode> elasticNodes
    HttpClient httpClient
    Random random = new Random()
    boolean useTimeouts;

    CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
    RetryRegistry retryRegistry = RetryRegistry.ofDefaults()
    Retry globalRetry

    static ElasticClient withDefaultHttpClient(List<String> elasticHosts, boolean useTimeouts) {
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager()
        cm.setMaxTotal(CONNECTION_POOL_SIZE)
        cm.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_HOST)

        HttpClient httpClient = new DefaultHttpClient(cm)
        HttpParams httpParams = httpClient.getParams()
        if (useTimeouts) {
            HttpConnectionParams.setConnectionTimeout(httpParams, CONNECT_TIMEOUT_MS)
            HttpConnectionParams.setSoTimeout(httpParams, READ_TIMEOUT_MS)
        } else {
            HttpConnectionParams.setConnectionTimeout(httpParams, 0)
            HttpConnectionParams.setSoTimeout(httpParams, 0)
        }
        // FIXME: upgrade httpClient (and use RequestConfig)- https://issues.apache.org/jira/browse/HTTPCLIENT-1418
        // httpParams.setParameter(ClientPNames.CONN_MANAGER_TIMEOUT, new Long(TIMEOUT_MS));

        return new ElasticClient(httpClient, elasticHosts, useTimeouts)
    }

    ElasticClient(HttpClient httpClient, List<String> elasticHosts, boolean useTimeouts) {
        this.httpClient = httpClient
        this.elasticNodes = elasticHosts.collect { new ElasticNode(it) }
        this.useTimeouts = useTimeouts;

        if (useTimeouts) {
            globalRetry = retryRegistry.retry(ElasticClient.class.getSimpleName(), RetryConfig.custom()
                    .waitDuration(Duration.ofMillis(10))
                    .maxAttempts(elasticNodes.size() * 2)
                    .build())

            CollectorRegistry collectorRegistry = CollectorRegistry.defaultRegistry
            collectorRegistry.register(RetryMetricsCollector.ofRetryRegistry(retryRegistry))
            collectorRegistry.register(CircuitBreakerMetricsCollector.ofCircuitBreakerRegistry(circuitBreakerRegistry))
        }

        log.info "ElasticSearch component initialized with ${elasticHosts.size()} nodes."
    }

    Tuple2<Integer, String> performRequest(String method, String path, String body, String contentType0 = null) {
        if (useTimeouts) {
            try {
                def nodes = cycleNodes()
                return globalRetry.executeSupplier({ -> nodes.next().performRequest(method, path, body, contentType0) })
            }
            catch (Exception e) {
                log.warn("Request to ElasticSearch failed: ${e}", e)
                throw new ElasticIOException(e.getMessage(), e)
            }
        } else {
            elasticNodes[random.nextInt(elasticNodes.size())].performRequest(method, path, body, contentType0)
        }
    }

    private Iterator<ElasticNode> cycleNodes() {
        def cycle = Iterators.cycle(elasticNodes)
        Iterators.advance(cycle, random.nextInt(elasticNodes.size()))
        return cycle
    }

    class ElasticNode {
        String host
        Function<HttpRequestBase, Tuple2<Integer, String>> send

        ElasticNode(String host) {
            this.host = host

            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(host, CB_CONFIG)

            Retry tryTwice = retryRegistry.retry(host, RetryConfig.custom()
                    .maxAttempts(2)
                    .retryOnException({ !(it instanceof RetriesExceededException) }).build())

            this.send = CircuitBreaker.decorateFunction(cb, Retry.decorateFunction(tryTwice, this.&sendRequest))
        }

        Tuple2<Integer, String> performRequest(String method, String path, String body, String contentType0 = null) {
            if (useTimeouts)
                return send.apply(buildRequest(method, path, body, contentType0))
            else
                return sendRequest(buildRequest(method, path, body, contentType0))
        }

        private Tuple2<Integer, String> sendRequest(HttpRequestBase request) {
            try {
                return sendRequestRetry429(request)
            }
            catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e)
            }
            finally {
                request.reset()
                request.releaseConnection()
            }
        }

        private Tuple2<Integer, String> sendRequestRetry429(HttpRequestBase request) {
            int backOffSeconds = 1
            while (true) {
                HttpResponse response = httpClient.execute(request)
                int statusCode = response.getStatusLine().getStatusCode()

                if (statusCode != 429) {
                    return new Tuple2(statusCode, EntityUtils.toString(response.getEntity()))
                } else {
                    if (backOffSeconds > MAX_BACKOFF_S) {
                        throw new RetriesExceededException("Max retries exceeded: HTTP 429 from ElasticSearch")
                    }

                    request.reset()

                    log.info("Bulk indexing request to ElasticSearch was throttled (HTTP 429) waiting $backOffSeconds seconds before retry.")
                    Thread.sleep(backOffSeconds * 1000)

                    backOffSeconds *= 2
                }
            }
        }

        private HttpRequestBase buildRequest(String method, String path, String body, String contentType0 = null) {
            switch (method) {
                case 'GET':
                    return new HttpGet(host + path)
                case 'PUT':
                    HttpPut request = new HttpPut(host + path)
                    request.setEntity(httpEntity(body, contentType0))
                    return request
                case 'POST':
                    HttpPost request = new HttpPost(host + path)
                    request.setEntity(httpEntity(body, contentType0))
                    return request
                default:
                    throw new IllegalArgumentException("Bad request method:" + method)
            }
        }

        private static HttpEntity httpEntity(String body, String contentType) {
            return new StringEntity(body,
                    contentType ? ContentType.create(contentType) : ContentType.APPLICATION_JSON)
        }
    }

    class RetriesExceededException extends RuntimeException {
        RetriesExceededException(String msg) {
            super(msg)
        }
    }
}
