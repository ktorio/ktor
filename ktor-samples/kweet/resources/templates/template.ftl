<#-- @ftlvariable name="user" type="kweet.model.User" -->

<#macro mainLayout title="Welcome">
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">

    <title>${title} | Kweet</title>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
</head>
<body>
<div class="page">
    <h1>Kweet</h1>
    <div class="navigation">
        <#if user??>
            <a href="/user/${user.userId}">my timeline</a> |
            <a href="/post-new">New kweet</a> |
            <a href="/logout">sign out [${user.displayName}]</a>
        <#else>
            <a href="/register">sign up</a> |
            <a href="/login">sign in</a>
        </#if>
    </div>
    <div class="body">
        <h2>${title}</h2>
        <#nested />
    </div>
    <div class="footer">
        Kweet ktor example, ${.now?string("yyyy")}
    </div>
</div>
</body>
</html>
</#macro>

<#-- @ftlvariable name="kweet" type="java.util.List<kweet.model.Kweet>" -->
<#macro kweet_li kweet>
<li><a href="/kweet/${kweet.id}">${kweet.text}</a> (by ${kweet.userId})</li>
</#macro>

<#macro kweets_list kweets>
<ul>
    <#list kweets as kweet>
        <@kweet_li kweet=kweet></@kweet_li>
    <#else>
        <li>There are no kweets yet</li>
    </#list>
</ul>
</#macro>