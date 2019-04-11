<#function pluralize number singlarNoun pluralNoun>
  <#local plural = number gt 1 />
  <#return number + " " + plural?string(pluralNoun, singlarNoun)>
</#function>

<#macro formatJarLinkageReport jarLinkageReport jarToDependencyPaths dependencyPathRootCauses>
  <#if jarLinkageReport.getErrorCount() gt 0>
    <#assign jarPath = jarLinkageReport.getJarPath() />
    <h3>${jarPath.getFileName()?html}</h3>

    <#assign causeToSourceClasses = jarLinkageReport.getCauseToSourceClasses() />
    <#assign targetClassCount = causeToSourceClasses.keySet()?size />
    <#assign sourceClassCount = jarLinkageReport.getErrorCount() />
    <p class="jar-linkage-report">
      ${pluralize(targetClassCount, "target class", "target classes")}
      causing linkage errors referenced from
      ${pluralize(sourceClassCount, "source class", "source classes")}.
    </p>
    <#list causeToSourceClasses.keySet() as errorCause >
      <#assign sourceClasses = causeToSourceClasses.get(errorCause) />
      <p class="jar-linkage-report-cause">${errorCause?html}, referenced from ${
        pluralize(sourceClasses?size, "source class", "source classes")?html}
        <button onclick="toggleSourceClassListVisibility(this)"
                title="Toggle visibility of source class list">▶
        </button>
      </p>

      <!-- The visibility of this list is toggled via the button above. Hidden by default -->
      <ul class="jar-linkage-report-cause" style="display:none">
        <#list sourceClasses as sourceClass>
          <li>${sourceClass?html}</li>
        </#list>
      </ul>
    </#list>
    <p class="linkage-check-dependency-paths">
      The following paths to the jar file from the BOM are found in the dependency tree: </p>
    <#if dependencyPathRootCauses[jarPath]?? >
      <p class="linkage-check-dependency-paths">${dependencyPathRootCauses[jarPath]?html}
      </p>
    <#else>
      <ul class="linkage-check-dependency-paths">
          <#list jarToDependencyPaths.get(jarPath) as dependencyPath >
            <li>${dependencyPath}</li>
          </#list>
      </ul>
    </#if>
  </#if>
</#macro>

<#macro formatDependencyNode currentNode parent>
  <#if parent == currentNode>
    <#assign label = 'root' />
  <#else>
    <#assign label = 'parent: ' + parent.getLeaf() />
  </#if>
  <p class="dependency-tree-node" title="${label}">${currentNode.getLeaf()}</p>
  <ul>
    <#list dependencyTree.get(currentNode) as childNode>
      <li class="dependency-tree-node">
        <@formatDependencyNode childNode currentNode />
      </li>
    </#list>
  </ul>
</#macro>

<#macro testResult row name>
  <#if row.getResult(name)?? ><#-- checking isNotNull() -->
  <#-- When it's not null, the test ran. It's either PASS or FAIL -->
    <#assign test_label = row.getResult(name)?then('PASS', 'FAIL')>
    <#assign failure_count = row.getFailureCount(name)>
  <#else>
  <#-- Null means there's an exception and test couldn't run -->
    <#assign test_label = "UNAVAILABLE">
  </#if>
  <td class='${test_label?lower_case}' title="${row.getExceptionMessage()!""}">
    <#if row.getResult(name)?? >
      <#assign page_anchor =  name?replace(" ", "-")?lower_case />
      <a href="${row.getCoordinates()?replace(":", "_")}.html#${page_anchor}">
        <#if failure_count gt 0>${pluralize(failure_count, "FAILURE", "FAILURES")}
        <#else>PASS
        </#if>
      </a>
    <#else>UNAVAILABLE
    </#if>
  </td>
</#macro>
