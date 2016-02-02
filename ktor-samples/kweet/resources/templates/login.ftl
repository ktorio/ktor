<#-- @ftlvariable name="error" type="java.lang.String" -->
<#-- @ftlvariable name="userId" type="java.lang.String" -->

<#import "template.ftl" as layout />

<@layout.mainLayout title="Welcome">
<form action="/login" method="post" enctype="application/x-www-form-urlencoded">
    <#if error??>
        <p class="error">${error}</p>
    </#if>

    <ul>
        <li><label for="userId">Login</label></li>
        <li><input type="text" name="userId" id="userId" value="${userId}"></li>

        <li><label for="password">Password</label></li>
        <li><input type="password" name="password" id="password"></li>

        <li><input type="submit" value="Register"></li>
    </ul>
</form>
</@layout.mainLayout>
