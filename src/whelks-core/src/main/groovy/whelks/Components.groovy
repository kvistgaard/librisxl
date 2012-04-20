package se.kb.libris.conch.component

import se.kb.libris.whelks.Document
import se.kb.libris.conch.*

interface Component {
    def add(Document d)
}

interface Storage extends Component {
    def add(Document d)
    def retrieve(URI u)
}

interface Index extends Component {
    def find(def query)
}

class DiskStorage implements Storage { 
    def storageDir = "./storage/"

    DiskStorage() {
        init()
    }

    DiskStorage(def directoryName) {
        setStorageDir(directoryName)
    }

    def init() {
        new File(storageDir).mkdirs()
    }

    def setStorageDir(String directoryName) { 
        println "Callin setStorageDir"
        storageDir = directoryName 
        init()
    }

    def add(Document d) {
        def filename = (d.identifier ? d.identifier.toString() : _create_filename())
        println "${this.class.name} storing file $filename in $storageDir"
        File file = new File("$storageDir/$filename")
        file.write(new String(d.data))
        d.identifier = new URI(filename)
    }

    MyDocument retrieve(URI u) {
        def s 
        def filename = u.toString()
        File f = new File("$storageDir/$filename")
        println "Filecontents:"
        println f.text
        return new MyDocument(f.text)
    }

    def _create_filename() {
        def pool = ['a'..'z','A'..'Z',0..9,'_'].flatten()
        Random rand = new Random(System.currentTimeMillis())

        def passChars = (0..10).collect { pool[rand.nextInt(pool.size())] }
        passChars.join()
    }
}
