<!DOCTYPE html>
<html lang="en-US">
  <#include "macros.ftl">
  <head>
    <meta charset="utf-8" />
    <title>Google Cloud Platform Java Open Source Dependency Dashboard: Unstable Artifacts</title>
    <link rel="stylesheet" href="dashboard.css" />
    <script src="dashboard.js"></script>
  </head>
  <body>
    <h1>Dependency Status of ${coordinates}</h1>
    <p class="bom-coordinates">BOM: ${coordinates?html}</p>
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