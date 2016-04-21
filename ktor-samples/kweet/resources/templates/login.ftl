<#-- @ftlvariable name="error" type="java.lang.String" -->
<#-- @ftlvariable name="userId" type="java.lang.String" -->

<#import "template.ftl" as layout />

<@layout.mainLayout title="Welcome">
<form class="pure-form-stacked" action="/login" method="post" enctype="application/x-www-form-urlencoded">
    <#if error??>
        <p class="error">${error}</p>
    </#if>

    <label for="userId">Login
        <input type="text" name="userId" id="userId" value="${userId}">
    </label>


    <label for="password">Password
        <input type="password" name="password" id="password">
    </label>

    <input class="pure-button pure-button-primary" type="submit" value="Login">
</form>
</@layout.mainLayout>
