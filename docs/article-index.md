---
permalink: /article-index
exclude_from_search: true
---
# Article index

{% for p in site.pages %}{% if p.article -%}
- [{{ p.title}}]({{ p.url | relative_url }})
{% endif %}{% endfor -%}
