<#-- @ftlvariable name="date" type="java.lang.Long" -->
<#-- @ftlvariable name="code" type="java.lang.String" -->
<#-- @ftlvariable name="kweet" type="kweet.model.Kweet" -->
<#import "template.ftl" as layout />

<@layout.mainLayout title="New kweet">
<h3>Kweet <small>(${kweet.id})</small></h3>
<p>Date: ${kweet.date.toDate()?string("yyyy.MM.dd HH:mm:ss")}</p>
<p>Text: </p>
<pre>
    ${kweet.text}
</pre>

<#if user??>
<p>
    <a href="javascript:void(0)" onclick="document.getElementById('deleteForm').submit()">Delete kweet</a>
</p>

<form id="deleteForm" method="post" action="/kweet/${kweet.id}/delete" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="date" value="${date?c}">
    <input type="hidden" name="code" value="${code}">
</form>
</#if>

</@layout.mainLayout>