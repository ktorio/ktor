<#-- @ftlvariable name="top" type="java.util.List<kweet.model.Kweet>" -->
<#-- @ftlvariable name="latest" type="java.util.List<kweet.model.Kweet>" -->

<#import "template.ftl" as layout />

<@layout.mainLayout title="Welcome">
<h3>Top 10</h3>
<@layout.kweets_list kweets=top></@layout.kweets_list>

<h3>Recent 10</h3>
<@layout.kweets_list kweets=latest></@layout.kweets_list>

</@layout.mainLayout>
