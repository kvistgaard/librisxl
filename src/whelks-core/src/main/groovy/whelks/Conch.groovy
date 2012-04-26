package se.kb.libris.conch

import java.net.URI
import org.restlet.*

import se.kb.libris.whelks.Document
import se.kb.libris.whelks.storage.Storage
import se.kb.libris.whelks.basic.BasicWhelk
import se.kb.libris.whelks.basic.BasicDocument

import se.kb.libris.conch.component.*

class MyDocument extends BasicDocument {
    URI identifier

    MyDocument() {
        generate_identifier()
    }

    MyDocument(URI uri) {
        this.identifier = uri
    }
    MyDocument(String uri) {
        this.identifier = new URI(uri)
    }
}

class App {
    static main(args) {
        def env = System.getenv()
        def whelk_storage = (env['PROJECT_HOME'] ? env['PROJECT_HOME'] : System.getProperty('user.home')) + "/whelk_storage"
        def storage = new DiskStorage(whelk_storage)
        def index = new ElasticSearchClient()
        def whelk = new Whelk(storage, index)
        whelk.name = "whelk"


        /*
        api.query('Fragile Things')
        */

        if (args.length > 1 && args[0] == 'retrieve') {
            println "Loading file ${args[1]}"
            def ldoc = whelk.retrieve(args[1])
            println "File:"
            println new String(ldoc.data)
        }
        else if (args.length > 1 && args[0] == 'find') {
            whelk.find(args[1])
        } 
        else if (args.length > 0) {

            def file = new File(args[0])
            println "Storing file ${file} (${file.name})"
            println "${file.text}"

            def doc = new MyDocument(file.name).withData(file.text.getBytes('UTF-8'))
            doc.type = "marc21"
            def identifier = whelk.ingest(doc)
            println "Stored document with identifier ${identifier}"
            println "Now trying to fetch it ..."
            whelk.find(file.name)
        }
    }
}
