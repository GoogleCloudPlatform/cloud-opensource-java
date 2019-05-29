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
    <h1>Google Cloud Platform Java Dependency Dashboard</h1>
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
    
    <section class="piecharts">
      <table><tr>
      <td style="vertical-align:top">
      <h3>Linkage Errors</h3>
      
      <#assign ratio = linkageErrorCount / totalArtifacts >
      <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
      <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
      
      <p>${linkageErrorCount} out of ${totalArtifacts} artifacts 
         ${plural(linkageErrorCount, "has", "have")} linkage errors.</p>
      
      <div>
        <svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">
          <desc>${linkageErrorCount} out of ${totalArtifacts} artifacts have linkage errors.</desc>
          <circle cx="100" cy="100" r="100" stroke-width="3" fill="green" />
          <path d="M100,100 v -100 A100,100 0 0 1 ${endPointX}, ${endPointY} z" fill="red" />
        </svg>
      </div>
     </td><td style="vertical-align:top">
     <h3>Local Upper Bounds</h3>
      
      <#assign ratio = localUpperBoundsErrorCount / totalArtifacts >
      <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
      <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
      
      <p>${localUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
         ${plural(localUpperBoundsErrorCount, "does not", "do not")} pick the
         latest versions of all artifacts in their own dependency tree.</p>
      
      <div>
        <svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">
          <desc>{localUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
         ${plural(localUpperBoundsErrorCount, "does not", "do not")} pick the
         latest versions of all artifacts in their own dependency tree.</desc>
          <circle cx="100" cy="100" r="100" stroke-width="3" fill="green" />
          <path d="M100,100 v -100 A100,100 0 0 1 ${endPointX}, ${endPointY} z" fill="red" />
        </svg>
      </div>
      </td>
      <td style="vertical-align:top">
      
     <h3>Global Upper Bounds</h3>
      
      <#assign ratio = globalUpperBoundsErrorCount / totalArtifacts >
      <#assign largeArcFlag = "0">
      <#if ratio gt 0.5>
        <#assign largeArcFlag = "1">
      </#if>
      <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
      <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
      
      <p>${globalUpperBoundsErrorCount} out of ${totalArtifacts} artifacts 
         ${plural(globalUpperBoundsErrorCount, "does not", "do not")} select the
         most recent version of all artifacts in the BOM.</p>
      
      <div>
        <svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">
          <desc>${globalUpperBoundsErrorCount} out of ${totalArtifacts} artifacts
               have global upper bounds errors.</desc>
          <circle cx="100" cy="100" r="100" stroke-width="3" fill="green" />
          <path d="M100,100 v -100 A100,100 0 ${largeArcFlag} 1 ${endPointX}, ${endPointY} z" fill="red" />
        </svg>
      </div>
    </td>
    <td style="vertical-align:top">
    <h3>Dependency Convergence</h3>
      
      <#assign ratio = convergenceErrorCount / totalArtifacts >
      <#assign largeArcFlag = "0">
      <#if ratio gt 0.5>
        <#assign largeArcFlag = "1">
      </#if>
      <#assign endPointX = pieChart.calculateEndPointX(100, 100, 100, ratio)>
      <#assign endPointY = pieChart.calculateEndPointY(100, 100, 100, ratio)>
      
      <p>${convergenceErrorCount} out of ${totalArtifacts} artifacts 
         ${plural(convergenceErrorCount, "fails", "fail")} to converge.</p>
      
      <div>
        <svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">
          <desc>${convergenceErrorCount} out of ${totalArtifacts} artifacts 
          ${plural(convergenceErrorCount, "fails", "fail")} to converge.</desc>
          <circle cx="100" cy="100" r="100" stroke-width="3" fill="green" />
          <path d="M100,100 v -100 A100,100 0 ${largeArcFlag} 1 ${endPointX}, ${endPointY} z" fill="red" />
        </svg>
      </div>
      </td></tr></table>
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