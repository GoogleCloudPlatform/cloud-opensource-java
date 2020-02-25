---
page:
  title: Google Best Practices for Java Libraries
permalink: /
exclude_from_search: true
---

# Google Best Practices for Java Libraries

Google Best Practices for Java Libraries are rules that minimize
problems for consumers of interconnected Java libraries. These practices come
from decades of aggregated experience in maintaining open source Java libraries
and are informed by many hard-learned lessons from mistakes that have been
made. We have found that following these rules results in higher quality
Java libraries with fewer dependency conflicts and other kinds of problems. The
list is open-ended, so new ones may be added from time to time.

## Best practices

{% for p in site.pages %}{% if p.jlbp -%}
- [{{ p.jlbp.id }}]({{ p.url | relative_url }}): {{ p.title }}
{% endif %}{% endfor -%}

## Articles

{% for p in site.pages %}{% if p.article -%}
- [{{ p.title}}]({{ p.url | relative_url }})
{% endif %}{% endfor -%}

## Reference

- [Glossary](glossary.md): Terms used in the best practices and other places in
  cloud-opensource-java.
