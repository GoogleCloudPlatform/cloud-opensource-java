<#function pluralize number singularNoun pluralNoun>
  <#local plural = number gt 1 />
  <#return number + " " + plural?string(pluralNoun, singularNoun)>
</#function>
<#-- same as above but without the number -->
<#function plural number singularNoun pluralNoun>
  <#local plural = number gt 1 />
  <#return plural?string(pluralNoun, singularNoun)>
</#function>

<#macro formatJarLinkageReport classPathEntry linkageProblems classPathResult
    dependencyPathRootCauses>
  <#-- problemsToClasses: ImmutableMap<LinkageProblem, ImmutableList<String>> to get key and set of
    values in Freemarker -->
  <#assign problemsToClasses = linkageProblem.groupBySymbolProblem(linkageProblems) />
  <#assign symbolProblemCount = problemsToClasses?size />
  <#assign referenceCount = 0 />
  <#list problemsToClasses?values as classes>
    <#assign referenceCount += classes?size />
  </#list>

  <h3>${classPathEntry?html}</h3>
  <p class="jar-linkage-report">
    ${pluralize(symbolProblemCount, "target class", "target classes")}
    causing linkage errors referenced from
    ${pluralize(referenceCount, "source class", "source classes")}.
  </p>
  <#list problemsToClasses as problem, sourceClasses>
    <#if sourceClasses?size == 1>
      <#assign sourceClass = sourceClasses[0] />
      <p class="jar-linkage-report-cause">${problem?html}, referenced from ${sourceClass?html}</p>
    <#else>
      <p class="jar-linkage-report-cause">${problem?html}, referenced from ${
          pluralize(sourceClasses?size, "class", "classes")?html}
        <button onclick="toggleNextSiblingVisibility(this)"
                title="Toggle visibility of source class list">▶
        </button>
      </p>
      <!-- The visibility of this list is toggled via the button above. Hidden by default -->
      <ul class="jar-linkage-report-cause" style="display:none">
          <#list sourceClasses as sourceClass>
            <li>${sourceClass?html}</li>
          </#list>
      </ul>
    </#if>
  </#list>
  <#assign jarsInProblem = {} >
  <#list linkageProblems as problem>
    <#if (problem.getTargetClass())?? >
      <#assign targetClassPathEntry = problem.getTargetClass().getClassPathEntry() />
      <#-- Freemarker's hash requires its keys to be strings.
      https://freemarker.apache.org/docs/app_faq.html#faq_nonstring_keys -->
      <#assign jarsInProblem = jarsInProblem + { targetClassPathEntry.toString() : targetClassPathEntry } >
    </#if>
  </#list>
  <#list jarsInProblem?values as jarInProblem>
    <@showDependencyPath dependencyPathRootCauses classPathResult jarInProblem />
  </#list>
  <#if !jarsInProblem?values?seq_contains(classPathEntry) >
    <@showDependencyPath dependencyPathRootCauses classPathResult classPathEntry />
  </#if>

</#macro>

<#macro showDependencyPath dependencyPathRootCauses classPathResult classPathEntry>
  <#assign dependencyPaths = classPathResult.getDependencyPaths(classPathEntry) />
  <#assign hasRootCause = dependencyPathRootCauses[classPathEntry]?? />
  <#assign hideDependencyPathsByDefault = (!hasRootCause) && (dependencyPaths?size > 5) />
  <p class="linkage-check-dependency-paths">
    The following ${plural(dependencyPaths?size, "path contains", "paths contain")} ${classPathEntry?html}:
    <#if hideDependencyPathsByDefault>
      <#-- The dependency paths are not summarized -->
      <button onclick="toggleNextSiblingVisibility(this)"
              title="Toggle visibility of source class list">▶
      </button>
    </#if>
  </p>

  <#if hasRootCause>
    <p class="linkage-check-dependency-paths">${dependencyPathRootCauses[classPathEntry]?html}
    </p>
  <#else>
    <!-- The visibility of this list is toggled via the button above. Hidden by default -->
    <ul class="linkage-check-dependency-paths"
        style="display:${hideDependencyPathsByDefault?string('none', '')}">
        <#list dependencyPaths as dependencyPath >
          <li>${dependencyPath}</li>
        </#list>
    </ul>
  </#if>
</#macro>

<#macro formatDependencyGraph graph node parent>
  <#if node == graph.getRootPath() >
      <#assign label = 'root' />
  <#else>
      <#assign label = 'parent: ' + parent.getLeaf() />
  </#if>
  <p class="dependency-tree-node" title="${label}">${node.getLeaf()}</p>
  <ul>
    <#list graph.getChildren(node) as childNode>
      <#if node != childNode>
        <li class="dependency-tree-node">
            <@formatDependencyGraph graph childNode node />
        </li>
      </#if>
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
