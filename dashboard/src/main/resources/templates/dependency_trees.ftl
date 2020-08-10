<!DOCTYPE html>
<html lang="en-US">
  <#include "macros.ftl">
  <head>
    <meta charset="utf-8" />
    <title>Google Cloud Platform Java Open Source Dependency Dashboard: Dependency Trees</title>
    <link rel="stylesheet" href="dashboard.css" />
    <script src="dashboard.js"></script>
  </head>
  <body>
    <h1>Dependency Tree of the Artifacts in ${coordinates}</h1>
    <p class="bom-coordinates">BOM: ${coordinates?html}</p>
    <#list dependencyGraphs as graph>
      <#assign rootPath = graph.getRootPath() />
      <h2>Dependency Tree of ${rootPath.getLeaf()?html}</h2>
      <@formatDependencyGraph graph rootPath "" />
    </#list>

    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>