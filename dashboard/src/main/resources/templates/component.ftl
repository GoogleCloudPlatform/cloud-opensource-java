<html lang="en-US">
  <head>
    <title>Google Cloud Platform Dependency Analysis Report for ${groupId}:${artifactId}:${version}</title>
    <link rel="stylesheet" type="text/css" href="dashboard.css" />
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


    <h2 id="static-linkage-errors">Static Linkage Check</h2>

    <p id="static-linkage-check">${totalLinkageErrorCount} static linkage error(s)</p>
    <#list jarLinkageReports as jarLinkageReport>
      <#if jarLinkageReport.getTotalErrorCount() gt 0>
        <pre class="jar-linkage-report">${jarLinkageReport?html}</pre>

        <p class="static-linkage-check-dependency-paths">
          Following paths to the jar file from BOM are found in the dependency tree.
        </p>
        <ul class="static-linkage-check-dependency-paths">
          <#list jarToDependencyPaths.get(jarLinkageReport.getJarPath()) as dependencyPath >
            <#assign dependencyPathRoot = dependencyPath.get(0) />
            <#assign linkedFromArtifact = dependencyPathRoot.getGroupId() == groupId
                && dependencyPathRoot.getArtifactId() == artifactId>
            <li class="linked-from-artifact-${linkedFromArtifact?c}">${dependencyPath}
            </li>
          </#list>
        </ul>

      </#if>
    </#list>

    <h2>Dependencies</h2>

    <#if dependencyRootNode?? >
      <@formatDependencyNode dependencyRootNode dependencyRootNode />
    <#else>
      <p>Dependency information is unavailable</p>
    </#if>

    <#macro formatDependencyNode currentNode parent>
      <#if parent == currentNode>
        <#assign label = 'root' />
      <#else>
        <#assign label = 'parent: ' + parent.getLeaf() />
      </#if>
      <p class="DEPENDENCY_TREE_NODE" title="${label}">${currentNode.getLeaf()}</p>
      <ul>
        <#list dependencyTree.get(currentNode) as childNode>
          <li class="DEPENDENCY_TREE_NODE">
            <@formatDependencyNode childNode currentNode />
          </li>
        </#list>
      </ul>
    </#macro>

    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>