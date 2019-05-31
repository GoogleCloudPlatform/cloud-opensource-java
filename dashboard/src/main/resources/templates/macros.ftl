<#function pluralize number singularNoun pluralNoun>
  <#local plural = number gt 1 />
  <#return number + " " + plural?string(pluralNoun, singularNoun)>
</#function>
<!-- same as above but without the number -->
<#function plural number singularNoun pluralNoun>
  <#local plural = number gt 1 />
  <#return plural?string(pluralNoun, singularNoun)>
</#function>

<#macro formatJarLinkageReport jar problemsWithClass jarToDependencyPaths dependencyPathRootCauses>
  <!-- problemsWithClass: ImmutableSetMultimap<SymbolProblem, String> converted to
    ImmutableMap<SymbolProblem, Collection<String>> to get key and set of values in Freemarker -->
  <#assign problemsToClasses = problemsWithClass.asMap() />
  <#assign symbolProblemCount = problemsToClasses?size />
  <#assign referenceCount = 0 />
  <#list problemsToClasses?values as classes>
    <#assign referenceCount += classes?size />
  </#list>

  <h3>${jar.getFileName()?html}</h3>
  <p class="jar-linkage-report">
    ${pluralize(symbolProblemCount, "symbol", "symbols")}
    causing linkage errors on
    ${pluralize(referenceCount, "reference", "references")}.
  </p>
  <#list problemsToClasses as symbolProblem, sourceClasses>
    <p class="jar-linkage-report-cause">${symbolProblem?html}, referenced from ${
      pluralize(sourceClasses?size, "class", "classes")?html}
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
    The following paths to the jar file from the BOM are found in the dependency tree:
  </p>
  <#if dependencyPathRootCauses[jar]?? >
    <p class="linkage-check-dependency-paths">${dependencyPathRootCauses[jar]?html}
    </p>
  <#else>
    <ul class="linkage-check-dependency-paths">
        <#list jarToDependencyPaths.get(jar) as dependencyPath >
          <li>${dependencyPath}</li>
        </#list>
    </ul>
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
