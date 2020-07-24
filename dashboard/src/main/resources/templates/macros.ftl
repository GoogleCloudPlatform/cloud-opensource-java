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
  <#-- problemsToClasses: ImmutableMap<String, ImmutableList<String>> where the key is
    LinkageProblem.formatSymbolProblem and the values are source classes -->
  <#assign problemsToClasses = linkageProblem.groupBySymbolProblem(linkageProblems) />

  <#-- problemsToCauses: ImmutableMap<String, ImmutableList<LinkageErrorCause>> where the key is
    LinkageProblem.formatSymbolProblem and the values are the causes of linkage errors -->
  <#assign problemsToCauses = linkageProblem.groupCausesBySymbolProblems(linkageProblems) >

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
    <p class="jar-linkage-report-linkage-problem">${problem?html}, referenced from ${
      pluralize(sourceClasses?size, "class", "classes")?html}
      <button onclick="toggleLinkageErrorDetailVisibility(this)"
              title="Toggle visibility of source class list">▶
      </button>
    </p>

    <div style="display:none">
      <!-- The visibility of this list is toggled via the button above. Hidden by default -->
      <ul class="jar-linkage-report-source-class">
          <#list sourceClasses as sourceClass>
            <li>${sourceClass?html}</li>
          </#list>
      </ul>
      <#if (problemsToCauses[problem])?? >
        <#assign causes = problemsToCauses[problem] />
        <p class="jar-linkage-report-cause">Cause:</p>
        <ul class="jar-linkage-report-cause">
            <#list causes as cause>
                <#assign causeClassName = cause.class.simpleName />
                <li>${cause?html}</li>
            </#list>
        </ul>
      </#if>
    </div>

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
  <@showDependencyPath dependencyPathRootCauses classPathResult classPathEntry />

</#macro>

<#macro showDependencyPath dependencyPathRootCauses classPathResult classPathEntry>
  <#assign dependencyPaths = classPathResult.getDependencyPaths(classPathEntry) />
  <p class="linkage-check-dependency-paths">
    The following ${plural(dependencyPaths?size, "path contains", "paths contain")} ${classPathEntry?html}:
  </p>

  <#if dependencyPathRootCauses[classPathEntry]?? >
    <p class="linkage-check-dependency-paths">${dependencyPathRootCauses[classPathEntry]?html}
    </p>
  <#else>
    <ul class="linkage-check-dependency-paths">
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
