package com.example.automation

import com.example.data.local.AutomationWorkflowEntity

data class WorkflowDependencyGraphResult(
    val topologicalOrderIds: List<Long>,
    val missingWorkflowIds: Set<Long>,
    val circularCycles: List<List<Long>>,
    val maxDepth: Int,
    val resolvedNamesById: Map<Long, String>
)

/**
 * Resolves sub-workflow dependencies, detects cycles and missing dependencies,
 * and computes safe topological orderings for execution, validation, dry-run, and bundle export/import.
 */
object WorkflowDependencyResolver {

    fun collectDirectSubWorkflowRefs(steps: List<AutomationStep>): List<SubWorkflowReference> {
        return steps.flatMap { it.collectNestedSteps() }
            .filter { it.actionType == AutomationActionType.CALL_WORKFLOW && it.subWorkflowRef != null }
            .mapNotNull { it.subWorkflowRef }
    }

    suspend fun resolveDependencyGraph(
        rootWorkflowId: Long,
        rootSteps: List<AutomationStep>,
        workflowLoader: suspend (Long) -> Pair<AutomationWorkflowEntity, WorkflowDocument>?
    ): WorkflowDependencyGraphResult {
        val topologicalOrder = mutableListOf<Long>()
        val missingIds = linkedSetOf<Long>()
        val cycles = mutableListOf<List<Long>>()
        val resolvedNames = mutableMapOf<Long, String>()
        val visitedCompleted = mutableSetOf<Long>()
        var observedMaxDepth = 0

        suspend fun visit(
            wfId: Long,
            steps: List<AutomationStep>,
            callStack: List<Long>,
            depth: Int
        ) {
            if (depth > observedMaxDepth) observedMaxDepth = depth
            val refs = collectDirectSubWorkflowRefs(steps)
            for (ref in refs) {
                val targetId = ref.targetWorkflowId
                if (targetId <= 0L) {
                    missingIds.add(targetId)
                    continue
                }
                if (targetId in callStack) {
                    val cycleStart = callStack.indexOf(targetId)
                    cycles.add(callStack.subList(cycleStart, callStack.size) + targetId)
                    continue
                }
                if (targetId in visitedCompleted) {
                    continue
                }
                val loaded = workflowLoader(targetId)
                if (loaded == null) {
                    missingIds.add(targetId)
                    continue
                }
                val (childEntity, childDoc) = loaded
                resolvedNames[targetId] = childEntity.name
                visit(
                    wfId = targetId,
                    steps = childDoc.steps,
                    callStack = callStack + targetId,
                    depth = depth + 1
                )
            }
            if (visitedCompleted.add(wfId)) {
                topologicalOrder.add(wfId)
            }
        }

        visit(rootWorkflowId, rootSteps, listOf(rootWorkflowId), 0)
        return WorkflowDependencyGraphResult(
            topologicalOrderIds = topologicalOrder,
            missingWorkflowIds = missingIds,
            circularCycles = cycles,
            maxDepth = observedMaxDepth,
            resolvedNamesById = resolvedNames
        )
    }

    /**
     * Orders a collection of portable workflows so dependencies appear before parents.
     * Throws IllegalStateException if a circular dependency exists inside the imported bundle.
     */
    fun sortPortablePayloadsTopologically(
        payloads: List<PortableWorkflowPayload>
    ): Pair<List<PortableWorkflowPayload>, List<String>> {
        val byUuid = payloads.associateBy { it.workflowUuid }
        val byLegacyId = payloads.filter { it.legacyId > 0L }.associateBy { it.legacyId }
        val sorted = mutableListOf<PortableWorkflowPayload>()
        val state = mutableMapOf<String, Int>() // 1 = visiting, 2 = visited
        val cycleErrors = mutableListOf<String>()

        fun dfs(payload: PortableWorkflowPayload, trail: List<String>) {
            val key = payload.workflowUuid
            when (state[key]) {
                2 -> return
                1 -> {
                    cycleErrors.add("Circular dependency detected involving '${payload.name}'.")
                    return
                }
            }
            state[key] = 1
            val refs = collectDirectSubWorkflowRefs(payload.document.steps)
            for (ref in refs) {
                val depPayload = ref.targetWorkflowUuid.takeIf { it.isNotBlank() }?.let { byUuid[it] }
                    ?: ref.targetWorkflowId.takeIf { it > 0L }?.let { byLegacyId[it] }
                if (depPayload != null) {
                    dfs(depPayload, trail + depPayload.workflowUuid)
                }
            }
            for (depUuid in payload.dependencyUuids) {
                val depPayload = byUuid[depUuid]
                if (depPayload != null) {
                    dfs(depPayload, trail + depUuid)
                }
            }
            state[key] = 2
            sorted.add(payload)
        }

        payloads.forEach { dfs(it, listOf(it.workflowUuid)) }
        return sorted to cycleErrors
    }
}
