def where = """
  collection = 'bib' 
  AND data['@graph'][1]['instanceOf']['@type'] = '"Text"'
  AND data['@graph'][1]['instanceOf']['summary'] IS NOT NULL
  AND data['@graph'][0]['descriptionCreator']['@id'] != '"https://libris.kb.se/library/APP1"'
  AND deleted = false
"""

def badShape = ['@type', 'label', 'language'] as Set

selectBySqlWhere(where) { bib ->
    def instance = bib.graph[1]
    def work = instance.instanceOf

    def normalizedSummary = work.summary.collect { Map s ->
        if (s.keySet().containsAll(badShape)) {
            def lang = s.subMap(['@type', 'language'])
            s.remove('language')
            return [lang, s]
        }
        s
    }.flatten()

    def (toInstance, toWork) = normalizedSummary.split { Map s ->
        s.containsKey('label')
    }

    if (toInstance) {
        if (toWork) {
            work['summary'] = toWork
        } else {
            work.remove('summary')
        }

        instance['summary'] = (instance.summary ?: []) + toInstance
        bib.scheduleSave()
    }
}

def asList(x) {
    (x ?: []).with {it instanceof List ? it : [it] }
}
