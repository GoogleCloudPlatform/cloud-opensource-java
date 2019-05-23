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
    
    <section class="statistics">
      <div class="container">
        <div class="statistic-item statistic-item-green">
          <h2>${table?size}</h2>
          <span class="desc">Total Artifacts Checked</span>
        </div>
        <div class="statistic-item statistic-item-red">
          <#assign errorCount = dashboardMain.countFailures(table, "Linkage Errors")>
          <h2>${errorCount}</h2>
          <span class="desc">${(errorCount == 1)?then("Has", "Have")} Linkage Errors</span>
        </div>
        <div class="statistic-item statistic-item-yellow">
          <#assign errorCount = dashboardMain.countFailures(table, "Upper Bounds")>
          <h2>${dashboardMain.countFailures(table, "Upper Bounds")}</h2>
          <span class="desc">${(errorCount == 1)?then("Has", "Have")} Upper Bounds Errors</span>
        </div>
        <div class="statistic-item statistic-item-orange">
          <#assign errorCount = dashboardMain.countFailures(table, "Global Upper Bounds")>
          <h2>${dashboardMain.countFailures(table, "Global Upper Bounds")}</h2>
          <span class="desc">${(errorCount == 1)?then("Has", "Have")} Global Upper Bounds Errors</span>
        </div>
        <div class="statistic-item statistic-item-blue">
          <#assign errorCount = dashboardMain.countFailures(table, "Dependency Convergence")>
          <h2>${dashboardMain.countFailures(table, "Dependency Convergence")}</h2>
          <span class="desc">${(errorCount == 1)?then("Fails", "Fail")} to Converge</span>
        </div>
      </div>
    </section>
    
    <p>
    <a href="artifact_detils.html">Detailed Artifact Reports</a>
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
      These are dependencies found in the GCP orbit which have not yet reached 1.0.
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