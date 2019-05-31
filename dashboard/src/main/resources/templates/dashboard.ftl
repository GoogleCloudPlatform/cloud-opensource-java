<!DOCTYPE html>
<html lang="en-US">
  <#include "macros.ftl">
  <head>
    <meta charset="utf-8" />
    <title>Google Cloud Platform Java Open Source Dependency Dashboard</title>
    <link rel="stylesheet" href="dashboard.css" />
    <script src="dashboard.js"></script>
  </head>
  <body>
    <h1>Dependency Status of ${coordinates}</h1>
    <hr />
    <#assign totalArtifacts = table?size>
    
    <section class="statistics">
      <div class="container">
        <div class="statistic-item statistic-item-green">
          <h2>${table?size}</h2>
          <span class="desc">Total Artifacts Checked</span>
        </div>
        <div class="statistic-item statistic-item-red">
          <#assign linkageErrorCount = dashboardMain.countFailures(table, "Linkage Errors")>
          <h2>${linkageErrorCount}</h2>
          <span class="desc">${(linkageErrorCount == 1)?then("Has", "Have")} Linkage Errors</span>
        </div>
        <div class="statistic-item statistic-item-yellow">
          <#assign localUpperBoundsErrorCount = dashboardMain.countFailures(table, "Upper Bounds")>
          <h2>${dashboardMain.countFailures(table, "Upper Bounds")}</h2>
          <span class="desc">${(localUpperBoundsErrorCount == 1)?then("Has", "Have")} Upper Bounds Errors</span>
        </div>
        <div class="statistic-item statistic-item-orange">
          <#assign globalUpperBoundsErrorCount = dashboardMain.countFailures(table, "Global Upper Bounds")>
          <h2>${dashboardMain.countFailures(table, "Global Upper Bounds")}</h2>
          <span class="desc">${(globalUpperBoundsErrorCount == 1)?then("Has", "Have")} Global Upper Bounds Errors</span>
        </div>
        <div class="statistic-item statistic-item-blue">
          <#assign convergenceErrorCount = dashboardMain.countFailures(table, "Dependency Convergence")>
          <h2>${dashboardMain.countFailures(table, "Dependency Convergence")}</h2>
          <span class="desc">${(convergenceErrorCount == 1)?then("Fails", "Fail")} to Converge</span>
        </div>
      </div>
    </section>
    
    <#assign pieSize = 300 >
    <section id="piecharts">
      <table>
        <col width="${pieSize}" />
        <col width="${pieSize}" />
        <col width="${pieSize}" />
        <col width="${pieSize}" />
        <tr>
        <th title=
                "Linkage check result for the artifact and transitive dependencies. PASS means all symbol references have valid referents.">
          Linkage Errors</th>
        <th title=
          "For each transitive dependency the library pulls in, the highest version found anywhere in the dependency tree is picked.">
          Local Upper Bounds</th>
        <th title=
          "For each transitive dependency the library pulls in, the highest version found anywhere in the union of the BOM's dependency trees is picked.">
          Global Upper Bounds</th>
        <th title=
                "There is exactly one version of each dependency in the library's transitive dependency tree. That is, two artifacts with the same group ID and artifact ID but different versions do not appear in the tree. No dependency mediation is necessary.">
          Dependency Convergence</th>
        </tr>
        <tr>
          <td>${linkageErrorCount} out of ${totalArtifacts} artifacts 
           ${plural(linkageErrorCount, "has", "have")} linkage errors.</td>
          <td style="vertical-align:top">
           ${localUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
           ${plural(localUpperBoundsErrorCount, "does not", "do not")} pick the
           latest versions of all artifacts in their own dependency tree.
        </td>
        <td>${globalUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
           ${plural(globalUpperBoundsErrorCount, "does not", "do not")} select the
           most recent version of all artifacts in the BOM.</td>
        <td>${convergenceErrorCount} out of ${totalArtifacts} artifacts 
           ${plural(convergenceErrorCount, "fails", "fail")} to converge.
        </td>
      </tr>
      <tr>
        <td class='pie'>    
          <#assign linkageRatio = linkageErrorCount / totalArtifacts >
          <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, linkageRatio)>
          <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, linkageRatio)>
          <svg xmlns="http://www.w3.org/2000/svg" width="${pieSize}" height="${pieSize}">
            <desc>${linkageErrorCount} out of ${totalArtifacts} artifacts have linkage errors.</desc>
            <circle cx="100" cy="100" r="100" stroke-width="3" fill="lightgreen" />
            <path d="M100,100 v -100 A100,100 0 0 1 ${endPointX}, ${endPointY} z" fill="red" />
          </svg>
       </td>

       <td class='pie'>    
        <#assign ratio = localUpperBoundsErrorCount / totalArtifacts >
        <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
        <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
        
          <svg xmlns="http://www.w3.org/2000/svg" width="${pieSize}" height="${pieSize}">
            <desc>{localUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
           ${plural(localUpperBoundsErrorCount, "does not", "do not")} pick the
           latest versions of all artifacts in their own dependency tree.</desc>
            <circle cx="100" cy="100" r="100" stroke-width="3" fill="lightgreen" />
            <path d="M100,100 v -100 A100,100 0 0 1 ${endPointX}, ${endPointY} z" fill="red" />
          </svg>
        </td>
      
       <td class='pie'>    
        <#assign ratio = globalUpperBoundsErrorCount / totalArtifacts >
        <#assign largeArcFlag = "0">
        <#if ratio gt 0.5>
          <#assign largeArcFlag = "1">
        </#if>
        <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
        <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
          <svg xmlns="http://www.w3.org/2000/svg" width="${pieSize}" height="${pieSize}">
            <desc>${globalUpperBoundsErrorCount} out of ${totalArtifacts} artifacts
                 have global upper bounds errors.</desc>
            <circle cx="100" cy="100" r="100" stroke-width="3" fill="lightgreen" />
            <path d="M100,100 v -100 A100,100 0 ${largeArcFlag} 1 ${endPointX}, ${endPointY} z" fill="red" />
          </svg>
       </td>
        
       <td class='pie'>    
          <#assign ratio = convergenceErrorCount / totalArtifacts >
          <#assign largeArcFlag = "0">
          <#if ratio gt 0.5>
            <#assign largeArcFlag = "1">
          </#if>
          <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
          <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
          
            <svg xmlns="http://www.w3.org/2000/svg" width="${pieSize}" height="${pieSize}">
              <desc>${convergenceErrorCount} out of ${totalArtifacts} artifacts 
              ${plural(convergenceErrorCount, "fails", "fail")} to converge.</desc>
              <circle cx="100" cy="100" r="100" stroke-width="3" fill="lightgreen" />
              <path d="M100,100 v -100 A100,100 0 ${largeArcFlag} 1 ${endPointX}, ${endPointY} z" fill="red" />
            </svg>
          </td>
        </tr>
      </table>
    </section>
    
    <p>
    <a href="artifact_details.html">Detailed Artifact Reports</a>
    </p>

    <h2>Recommended Versions</h2>
      
    <p>These are the most recent versions of dependencies used by any of the covered artifacts.</p> 
      
    <ul id="recommended">
      <#list latestArtifacts as artifact, version>
        <li><code>${artifact}:${version}</code></li>
      </#list>
    </ul>
 
    <hr />
      
    <h2>Pre 1.0 Versions</h2>
      
    <p>
      These are dependencies found in the GCP orbit that have not yet reached 1.0.
      No 1.0 or later library should depend on them.
      If the libraries are stable, advance them to 1.0.
      Otherwise replace the dependency with something else.
    </p> 
    
    <#assign unstableCount = 0>
    <ul id="unstable">
      <#list latestArtifacts as artifact, version>
        <#if version[0] == '0'>
          <#assign unstableCount++>
          <li><code>${artifact}:${version}</code></li>
        </#if>
      </#list>
    </ul>
     
    <#if unstableCount == 0>
      <p id="stable-notice">All versions are 1.0 or later.</p> 
    </#if>
    
     
    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>