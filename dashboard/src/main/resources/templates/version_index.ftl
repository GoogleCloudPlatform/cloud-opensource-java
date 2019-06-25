<!DOCTYPE html>
<html lang="en-US">
<#include "macros.ftl">
<head>
  <meta charset="utf-8" />
  <title>${groupId}:${artifactId}</title>
  <link rel="stylesheet" href="dashboard.css" />
</head>
<body>
<h1>${groupId}:${artifactId}</h1>

<ul>
  <#list versions as version>
    <li><a href="./${version?contains('-SNAPSHOT')?then('snapshot', version)}/index.html">${version}</a></li>
  </#list>
</ul>

</body>
</html>