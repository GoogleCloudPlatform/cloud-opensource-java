<!DOCTYPE html>
<html lang="en-US">
  <#include "macros.ftl">
  <#assign groupId = artifact.getGroupId()>
  <#assign artifactId = artifact.getArtifactId()>
  <#assign version = artifact.getVersion()>
  <head>
    <meta charset="utf-8" />
    <title>Google Cloud Platform Dependency Analysis Report for ${groupId}:${artifactId}:${version}
    </title>
    <link rel="stylesheet" href="dashboard.css" />
    <script src="dashboard.js"></script>
  </head>
  <body>
    <h1>Dependency Analysis of ${groupId}:${artifactId}:${version}</h1>
    
    
    <h2 id="global-upper-bounds">Global Upper Bounds Check</h2>
    
    <p>For each transitive dependency the library pulls in, the highest version 
       found anywhere in the union of the BOM's dependency trees is picked.</p>

    <#if globalUpperBoundFailures?size gt 0>
      <h3 style="color: red">Global Upper Bounds Fixes</h3>
      
      <p>Suggested updates to bring this artifact into sync with the highest versions
       of its dependencies used by any artifact in the BOM:</p>
    
      <ul>
        <#list globalUpperBoundFailures as lower, upper>
          <#if lower.getGroupId() == groupId && lower.getArtifactId() == artifactId
              && lower.getVersion() == version >
            <!-- When this is upgrading a BOM member -->
            <li class="global-upper-bound-bom-upgrade">
              Upgrade ${lower} in the BOM to version "${upper.getVersion()}":

              <p>Update the version of managed dependency element
                ${groupId}:${artifactId} in the BOM:</p>

              <pre class="suggested-dependency-mediation"><code>&lt;dependencyManagement>
  &lt;dependencies>
    ...
    &lt;dependency>
      &lt;groupId>${upper.getGroupId()}&lt;/groupId>
      &lt;artifactId>${upper.getArtifactId()}&lt;/artifactId>
      &lt;version>${upper.getVersion()}&lt;/version>
    &lt;/dependency></code></pre>

            </li>
          <#else >
            <li class="global-upper-bound-dependency-upgrade">
              Upgrade ${lower} to version "${upper.getVersion()}":

              <p>Add this dependency element to the pom.xml for ${groupId}:${artifactId}:${version}:
              </p>

              <pre class="suggested-dependency-mediation"><code>&lt;dependency>
&lt;groupId>${upper.getGroupId()}&lt;/groupId>
&lt;artifactId>${upper.getArtifactId()}&lt;/artifactId>
&lt;version>${upper.getVersion()}&lt;/version>
&lt;/dependency></code></pre>

            </li>
          </#if>
        </#list>
      </ul>
      
      <p>If the pom.xml for ${groupId}:${artifactId}:${version} already includes this dependency,
        update the version of the existing <code>dependency</code> element. Otherwise add a new 
        <code>dependency</code> element to the <code>dependencyManagement</code> section.</p>
      
    <#else>
      <h3 style="color: green">
        ${groupId}:${artifactId}:${version} selects the highest version of all dependencies.
      </h3>
    </#if>   
    
    
    <h2 id="upper-bounds">Local Upper Bounds Check</h2>
    
    
    <p>For each transitive dependency the library pulls in, the highest version 
       found anywhere in the dependency tree is picked.</p>
    
   <#if upperBoundFailures?size gt 0>
      <h3 style="color: red">Upper Bounds Fixes</h3>
      
    <p>Suggested updates to bring this artifact into sync with the highest versions
       of each dependency found in its own dependency tree:</p>
      
      <ul>
        <#list upperBoundFailures as lower, upper>
          <li>Upgrade ${lower} to ${upper}:
          
          <p>Add this dependency element to the pom.xml for ${groupId}:${artifactId}:${version}:</p>
          
<pre class="suggested-dependency-mediation"><code>&lt;dependency>
  &lt;groupId>${upper.getGroupId()}&lt;/groupId>
  &lt;artifactId>${upper.getArtifactId()}&lt;/artifactId>
  &lt;version>${upper.getVersion()}&lt;/version>
&lt;/dependency></code></pre>
          
          </li>
        </#list>
      </ul>
      
      <p>If the pom.xml already includes a dependency on ${groupId}:${artifactId}, update the version
         on the existing <code>dependency</code> element. Otherwise add these <code>dependency</code>
         elements to the <code>dependencyManagement</code> section.</p>
      
    <#else>
      <h3 style="color: green">
        ${groupId}:${artifactId}:${version} selects the highest version of all dependencies.
      </h3>
    </#if>
        
    <h2 id="dependency-convergence">Dependency Convergence</h2>
    
    <p>There is exactly one version of each dependency in the library's transitive dependency tree.
       That is, two artifacts with the same group ID and artifact ID but different versions
       do not appear in the tree. No dependency mediation is necessary.</p>
    
    <#if updates?size gt 0>
      <h3 style="color: red">Suggested Dependency Updates</h3>
      
      <p>Caution: The algorithm for suggesting updates is imperfect.
         They are not ordered by importance, and one change 
         may render another moot.</p>
      
    <p>Suggested updates to bring this artifact and its dependencies 
       into sync with the highest versions
       of each dependency found in its own dependency tree:</p>      
      
      <ul>
        <#list updates as update>
          <li>${update}</li>
        </#list>
      </ul>
    <#else>
      <h3 style="color: green">${groupId}:${artifactId}:${version} Converges</h3>
    </#if>


    <h2 id="linkage-errors">Linkage Check</h2>

    <p id="linkage-errors-total">${totalLinkageErrorCount} linkage error(s)</p>
    <#list jarLinkageReports as jarLinkageReport>
      <@formatJarLinkageReport jarLinkageReport jarToDependencyPaths {} />
    </#list>

    <h2>Dependencies</h2>

    <#if dependencyRootNode?? >
      <@formatDependencyNode dependencyRootNode dependencyRootNode />
    <#else>
      <p>Dependency information is unavailable</p>
    </#if>
    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>