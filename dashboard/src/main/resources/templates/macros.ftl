<#macro formatJarLinkageReport jarLinkageReport jarToDependencyPaths>
  <h3>${jarLinkageReport.getJarPath().getFileName()?html}</h3>

  <#assign causeToSourceClasses = jarLinkageReport.getCauseToSourceClasses() />
  <#assign errorPlural = jarLinkageReport.getTotalErrorCount() gt 1 />
  <#assign causePlural = causeToSourceClasses.keySet()?size gt 1 />
  <#assign linkageErrors = jarLinkageReport.getTotalErrorCount()
  + " linkage error" + errorPlural?string('s', '') />
  <#assign classes = causeToSourceClasses.keySet()?size
  + " class" + causePlural?string('es', '') />
  <p class="jar-linkage-report">${linkageErrors} in ${classes}</p>

  <#list causeToSourceClasses.keySet() as key >
    <p class="jar-linkage-report-cause">${key?html} Referenced from</p>
    <ul class="jar-linkage-report-cause">
      <#list causeToSourceClasses.get(key) as sourceClass>
        <li class="jar-linkage-report-source-class"><code>${sourceClass?html}</code></li>
      </#list>
    </ul>
  </#list>
  <p class="static-linkage-check-dependency-paths">
    Following paths to the jar file from BOM are found in the dependency tree.
  </p>
  <ul class="static-linkage-check-dependency-paths">
    <#list jarToDependencyPaths.get(jarLinkageReport.getJarPath()) as dependencyPath >
      <li>${dependencyPath}</li>
    </#list>
  </ul>
</#macro>
<#macro formatDependencyNode currentNode parent>
  <#if parent == currentNode>
    <#assign label = 'root' />
  <#else>
    <#assign label = 'parent: ' + parent.getLeaf() />
  </#if>
  <p class="DEPENDENCY_TREE_NODE" title="${label}">${currentNode.getLeaf()}</p>
  <ul>
    <#list dependencyTree.get(currentNode) as childNode>
      <li class="DEPENDENCY_TREE_NODE">
        <@formatDependencyNode childNode currentNode />
      </li>
    </#list>
  </ul>
</#macro>
<#macro countFailures name>
  <#assign total = 0>
  <#list table as row>
    <#if row.getResult(name)?? >
      <#assign failure_count = row.getFailureCount(name)>
      <#if failure_count gt 0 >
        <#assign total = total + 1>
      </#if>
    <#else>
    <#-- Null means there's an exception and test couldn't run -->
      <#assign total = total + 1>
    </#if>
  </#list>
  ${total}
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
  <td class='${test_label}' title="${row.getExceptionMessage()!""}">
    <#if row.getResult(name)?? >
      <#assign page_anchor =  name?replace(" ", "-")?lower_case />
      <a href="${row.getCoordinates()?replace(":", "_")}.html#${page_anchor}">
        <#if failure_count == 1>1 FAILURE
        <#elseif failure_count gt 1>${failure_count} FAILURES
        <#else>PASS
        </#if>
      </a>
    <#else>UNAVAILABLE
    </#if>
  </td>
</#macro>
