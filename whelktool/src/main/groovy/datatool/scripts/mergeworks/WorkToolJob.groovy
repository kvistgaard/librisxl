package datatool.scripts.mergeworks


import whelk.Document
import whelk.IdGenerator
import whelk.JsonLd
import whelk.Whelk
import whelk.exception.WhelkRuntimeException
import whelk.util.LegacyIntegrationTools
import whelk.util.Statistics

import java.text.SimpleDateFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

import static datatool.scripts.mergeworks.FieldStatus.DIFF

import static datatool.scripts.mergeworks.Util.getPathSafe
import static datatool.scripts.mergeworks.Util.partition

class WorkToolJob {
    Whelk whelk
    Statistics statistics
    File clusters

    String date = new SimpleDateFormat('yyyyMMdd-HHmmss').format(new Date())
    String jobId = IdGenerator.generate()
    File reportDir = new File("reports/$date/merged-works")

    String changedIn = "xl"
    String changedBy = "SEK"
    String generationProcess = 'https://libris.kb.se/sys/merge-works'
    boolean dryRun = true
    boolean skipIndex = false
    boolean loud = false
    boolean verbose = false

    WorkToolJob(File clusters) {
        this.clusters = clusters

        this.whelk = Whelk.createLoadedSearchWhelk('secret', true)
        this.statistics = new Statistics()
    }

    public static Closure qualityMonographs = { Doc doc ->
        (doc.isText()
                && doc.isMonograph()
                && !doc.hasPart()
                && (doc.encodingLevel() != 'marc:PartialPreliminaryLevel' && doc.encodingLevel() != 'marc:PrepublicationLevel'))
                && !doc.hasRelationshipWithContribution()
    }

    void show() {
        println(Html.START)
        run({ cluster ->
            return {
                try {
                    Collection<Collection<Doc>> docs = titleClusters(cluster)

                    if (docs.isEmpty() || docs.size() == 1 && docs.first().size() == 1) {
                        return
                    }

                    println(docs
                            .collect { it.sort { a, b -> a.getWork()['@type'] <=> b.getWork()['@type'] } }
                            .collect { it.sort { it.numPages() } }
                            .collect { Html.clusterTable(it) }
                            .join('') + Html.HORIZONTAL_RULE
                    )
                }
                catch (NoWorkException e) {
                    System.err.println(e.getMessage())
                }
                catch (Exception e) {
                    System.err.println(e.getMessage())
                    e.printStackTrace(System.err)
                }
            }
        })
        println(Html.END)
    }

    void showWorks() {
        println(Html.START)
        run({ cluster ->
            return {
                try {
                    def merged = mergedWorks(titleClusters(cluster)).findAll { it.derivedFrom.size() > 1 }
                    if (merged) {
                        println(merged.collect { [new Doc(whelk, it.work)] + it.derivedFrom }
                                .collect { Html.clusterTable(it) }
                                .join('') + Html.HORIZONTAL_RULE
                        )
                    }
                }
                catch (Exception e) {
                    System.err.println(e.getMessage())
                    e.printStackTrace(System.err)
                }
            }
        })
        println(Html.END)
    }

    void showHubs() {
        println(Html.START)
        run({ cluster ->
            return {
                try {
                    def hub = mergedWorks(titleClusters(cluster))
                            .collect { [new Doc(whelk, it.work)] + it.derivedFrom }
                    if (hub.size() > 1) {
                        println(Html.hubTable(hub) + Html.HORIZONTAL_RULE)
                    }
                }
                catch (Exception e) {
                    System.err.println(e.getMessage())
                    e.printStackTrace(System.err)
                }
            }
        })
        println(Html.END)
    }

    void merge() {
        def s = statistics.printOnShutdown()
        reportDir.mkdirs()

        run({ cluster ->
            return {
                def titles = titleClusters(cluster)
                def works = mergedWorks(titles)

                works.each {
                    if (it.derivedFrom.size() > 1) {
                        store(it)
                    }
                }

                String report = htmlReport(titles, works)

                new File(reportDir, "${Html.clusterId(cluster)}.html") << report
                works.each {
                    s.increment('num derivedFrom', "${it.derivedFrom.size()}", it.work.shortId)
                    new File(reportDir, "${it.work.shortId}.html") << report
                }
            }
        })
    }

    void revert() {
        run({ cluster ->
            return {
                def docs = cluster.collect(whelk.&getDocument).grep()

                Set<String> works = []

                docs.each { Document d ->
                    def sum = d.getChecksum(whelk.jsonld)
                    works << getPathSafe(d.data, d.workIdPath)
                    def revertTo = whelk.storage.loadAllVersions(d.shortId)
                            .reverse()
                            .find { v -> getPathSafe(v.data, v.workIdPath) == null }
                    d.data = revertTo.data
                    d.setGenerationDate(new Date())
                    d.setGenerationProcess(generationProcess)
                    whelk.storeAtomicUpdate(d, !loud, changedIn, changedBy, sum)
                }

                works.grep().each {
                    def shortId = it.split("[#/]")[-2]
                    whelk.remove(shortId, changedIn, changedBy)
                }
            }
        })
    }

    String htmlReport(Collection<Collection<Doc>> titleClusters, Collection<MergedWork> works) {
        if (titleClusters.isEmpty() || titleClusters.size() == 1 && titleClusters.first().size() == 1) {
            return ""
        }

        StringBuilder s = new StringBuilder()

        s.append(Html.START)
        s.append("<h1>Title cluster(s)</h1>")
        titleClusters.each { it.each { it.addComparisonProps() } }

        titleClusters
                .collect { it.sort { a, b -> a.getWork()['@type'] <=> b.getWork()['@type'] } }
                .collect { it.sort { it.numPages() } }
                .each {
                    s.append(Html.clusterTable(it))
                    s.append(Html.HORIZONTAL_RULE)
                }
        titleClusters.each { it.each { it.removeComparisonProps() } }

        s.append("<h1>Extracted works</h1>")
        works.collect { [new Doc(whelk, it.work)] + it.derivedFrom }
                .each { s.append(Html.clusterTable(it)) }

        s.append(Html.END)
        return s.toString()
    }

    class MergedWork {
        Document work
        Collection<Doc> derivedFrom
    }

    private Document buildWorkDocument(Map workData) {
        String workId = IdGenerator.generate()

        workData['@id'] = "TEMPID#it"
        Document d = new Document([
                "@graph": [
                        [
                                "@id"          : "TEMPID",
                                "@type"        : "Record",
                                "mainEntity"   : ["@id": "TEMPID#it"],
                                "technicalNote": [[
                                                          "@type"  : "TechnicalNote",
                                                          "hasNote": [[
                                                                              "@type": "Note",
                                                                              "label": ["Maskinellt utbrutet verk... TODO"]
                                                                      ]],
                                                          "uri"    : ["http://xlbuild.libris.kb.se/works/$date/merged-works/${workId}.html".toString()]

                                                  ]
                                ]],
                        workData
                ]
        ])

        d.setGenerationDate(new Date())
        d.setGenerationProcess(generationProcess)
        d.deepReplaceId(Document.BASE_URI.toString() + workId)
        return d
    }

    private void store(MergedWork work) {
        if (!dryRun) {
            whelk.setSkipIndex(skipIndex)
            if (!whelk.createDocument(work.work, changedIn, changedBy,
                    LegacyIntegrationTools.determineLegacyCollection(work.work, whelk.getJsonld()), false)) {
                throw new WhelkRuntimeException("Could not store new work: ${work.work.shortId}")
            }

            String workIri = work.work.thingIdentifiers.first()

            work.derivedFrom
                    .collect { it.ogDoc }
                    .each {
                        def sum = it.getChecksum(whelk.jsonld)
                        it.data[JsonLd.GRAPH_KEY][1]['instanceOf'] = [(JsonLd.ID_KEY): workIri]
                        it.setGenerationDate(new Date())
                        it.setGenerationProcess(generationProcess)
                        whelk.storeAtomicUpdate(it, !loud, changedIn, changedBy, sum)
                    }
        }
    }

    private Collection<MergedWork> mergedWorks(Collection<Collection> titleClusters) {
        def works = []
        titleClusters.each { titleCluster ->
            titleCluster.sort { it.numPages() }
            WorkComparator c = new WorkComparator(WorkComparator.allFields(titleCluster))

            works.addAll(partition(titleCluster, { Doc a, Doc b -> c.sameWork(a, b) })
                    .each { work -> work.each { doc -> doc.removeComparisonProps() } }
                    .collect { new MergedWork(work: buildWorkDocument(c.merge(it)), derivedFrom: it) })
        }

        return works
    }


    void subTitles() {
        statistics.printOnShutdown(10)
        run({ cluster ->
            return {
                String titles = cluster.collect(whelk.&getDocument).collect {
                    getPathSafe(it.data, ['@graph', 1, 'hasTitle', 0, 'subtitle'])
                }.grep().join('\n')

                if (!titles.isBlank()) {
                    println(titles + '\n')
                }
            }
        })
    }

    void printInstanceValue(String field) {
        run({ cluster ->
            return {
                String values = cluster.collect(whelk.&getDocument).collect {
                    "${it.shortId}\t${getPathSafe(it.data, ['@graph', 1, field])}"
                }.join('\n')

                println(values + '\n')
            }
        })
    }

    void fictionNotFiction() {
        run({ cluster ->
            return {
                Collection<Collection<Doc>> titleClusters = titleClusters(cluster)

                for (titleCluster in titleClusters) {
                    if (titleCluster.size() > 1) {
                        def statuses = WorkComparator.compare(cluster)
                        if (!statuses[DIFF].contains('contribution')) {
                            String gf = titleCluster.collect { it.getDisplayText('genreForm') }.join(' ')
                            if (gf.contains('marc/FictionNotFurtherSpecified') && gf.contains('marc/NotFictionNotFurtherSpecified')) {
                                println(titleCluster.collect { it.getDoc().shortId }.join('\t'))
                            }
                        }
                    }
                }
            }
        })
    }

    void swedishFiction() {
        def swedish = { Doc doc ->
            Util.asList(doc.getWork()['language']).collect { it['@id'] } == ['https://id.kb.se/language/swe']
        }

        run({ cluster ->
            return {
                def c = loadDocs(cluster)
                        .findAll(qualityMonographs)
                        .findAll(swedish)
                        .findAll { d -> !d.isDrama() }

                if (c.any { it.isFiction() } && !c.any { it.isNotFiction() }) {
                    println(c.collect { it.doc.shortId }.join('\t'))
                }
            }
        })
    }

    void filterClusters(Closure<Collection<Doc>> predicate) {
        run({ cluster ->
            return {
                if (predicate(loadDocs(cluster))) {
                    println(cluster.join('\t'))
                }
            }
        })
    }

    void filterDocs(Closure<Doc> predicate) {
        run({ cluster ->
            return {
                def c = loadDocs(cluster).findAll(predicate)
                if (c.size() > 0) {
                    println(c.collect { it.doc.shortId }.join('\t'))
                }
            }
        })
    }

    void translationNoTranslator() {
        run({ cluster ->
            return {
                def c = loadDocs(cluster)

                if (c) {
                    if (c.any { it.isTranslation() }) {
                        if (c.any { it.hasTranslator() }) {
                            c = c.findAll { !it.isTranslationWithoutTranslator() }
                        } else {
                            int pages = c.first().numPages()
                            if (c.any { it.numPages() != pages }) {
                                return // drop cluster
                            }
                        }
                    }
                }

                if (c.size() > 0) {
                    println(c.collect { it.doc.shortId }.join('\t'))
                }
            }
        })
    }

    void outputTitleClusters() {
        run({ cluster ->
            return {
                titleClusters(cluster).findAll { it.size() > 1 }.each {
                    println(it.collect { it.doc.shortId }.join('\t'))
                }
            }
        })
    }

    private void run(Function<List<String>, Runnable> f) {
        ExecutorService s = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4)

        AtomicInteger i = new AtomicInteger()
        clusters.eachLine() {
            List<String> cluster = Arrays.asList(it.split(/[\t ]+/))

            s.submit({
                try {
                    f.apply(cluster).run()
                    int n = i.incrementAndGet()
                    if (n % 100 == 0) {
                        System.err.println("$n")
                    }
                }
                catch (NoWorkException e) {
                    //println("No work:" + e.getMessage())
                }
                catch (Exception e) {
                    e.printStackTrace()
                }
            })
        }

        s.shutdown()
        s.awaitTermination(1, TimeUnit.DAYS)
    }

    private Collection<Doc> loadDocs(Collection<String> cluster) {
        whelk
                .bulkLoad(cluster).values()
                .collect { new Doc(whelk, it) }
    }

    private Collection<Collection<Doc>> titleClusters(Collection<String> cluster) {
        loadDocs(cluster)
                .findAll(qualityMonographs)
                .each { it.addComparisonProps() }
                .with { partitionByTitle(it) }
                .findAll { it.size() > 1 }
                .findAll { !it.any { doc -> doc.hasGenericTitle() } }
                .sort { a, b -> a.first().mainEntityDisplayTitle() <=> b.first().mainEntityDisplayTitle() }
    }

    Collection<Collection<Doc>> partitionByTitle(Collection<Doc> docs) {
        return partition(docs) { Doc a, Doc b ->
            !a.getTitleVariants().intersect(b.getTitleVariants()).isEmpty()
        }
    }

}

class NoWorkException extends RuntimeException {
    NoWorkException(String msg) {
        super(msg)
    }
}









