/*
 * This removes legacy marc properties from link entries and
 * fix faulty mapping of otherPhysicalFormat
 *
 * otherPhysicalFormat.Instance.contribution --> otherPhysicalFormat.Instance.instanceOf.contribution
 * otherPhysicalFormat.Instance.marc:displayText --> otherPhysicalFormat.Instance.instanceOf.marc:displayText
 * otherPhysicalFormat.Instance.expressionOf.hasTitle --> otherPhysicalFormat.Instance.instanceOf.hasTitle
 *
 * See LXL-2769 for more info.
 */

PrintWriter failedIDs = getReportWriter("failed-to-update")
scheduledForChange = getReportWriter("scheduled-for-change")

PROPERTIES_TO_REMOVE = ['marc:toDisplayNote', 'marc:controlSubfield', 'partNumber', 'marc:fieldref']
PROPERTIES_TO_MOVE = ['contribution', 'marc:displayText']

selectBySqlWhere("""
        collection = 'bib' AND data#>>'{@graph,1,otherPhysicalFormat}' IS NOT NULL
    """) { data ->

    def (record, thing, work) = data.graph

    thing.otherPhysicalFormat.each {
        updateProperties(data, it)
    }
}

void updateProperties(data, object) {
    def somethingWasRemoved = removeProperties(object, PROPERTIES_TO_REMOVE)
    def workObjectsToMove = moveWorkObjects(object)

    if (!workObjectsToMove.isEmpty()) {
        if (object.instanceOf)
            object.instanceOf << workObjectsToMove
        else
            object['instanceOf'] = [(TYPE): 'Work'] << workObjectsToMove
    }

    if (somethingWasRemoved || workObjectsToMove) {
        scheduledForChange.println "Record was updated ${data.graph[0][ID]}"
        data.scheduleSave(onError: { e ->
            failedIDs.println("Failed to save ${data.graph[0][ID]} due to: $e")
        })
    }
}

boolean removeProperties(object, propertiesToRemove) {
    object.keySet().removeIf { propertiesToRemove.contains(it) }
}

Map moveWorkObjects(object) {
    Map workObjectsToMove
    workObjectsToMove = object.findAll { PROPERTIES_TO_MOVE.contains(it.key) }
    if (object['expressionOf'])
        workObjectsToMove << object.expressionOf.findAll { it.key != TYPE }
    removeProperties(object, PROPERTIES_TO_MOVE + 'expressionOf')
    return workObjectsToMove
}