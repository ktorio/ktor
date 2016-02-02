<#-- @ftlvariable name="error" type="java.lang.String" -->
<#-- @ftlvariable name="pageUser" type="kweet.model.User" -->

<#import "template.ftl" as layout />

<@layout.mainLayout title="Welcome">
<form action="/register" method="post" enctype="application/x-www-form-urlencoded">
    <#if error??>
        <p class="error">${error}</p>
    </#if>

    <ul>
        <li><label for="userId">Login</label></li>
        <li><input type="text" name="userId" id="userId" value="${pageUser.userId}"></li>

        <li><label for="email">Mail</label></li>
        <li><input type="email" name="email" id="email" value="${pageUser.email}"></li>

        <li><label for="displayName">Display name</label></li>
        <li><input type="text" name="displayName" id="displayName" value="${pageUser.displayName}"></li>

        <li><label for="password">Password</label></li>
        <li><input type="password" name="password" id="password"></li>

        <li><input type="submit" value="Register"></li>
    </ul>
</form>
</@layout.mainLayout>
