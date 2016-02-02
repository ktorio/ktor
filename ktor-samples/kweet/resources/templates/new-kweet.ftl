<#import "template.ftl" as layout />

<@layout.mainLayout title="New kweet">
<form action="/post-new" method="post" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="date" value="${date?c}">
    <input type="hidden" name="code" value="${code}">

    <ul>
        <li><label for="post-text">Text:</label></li>
        <li>
            <textarea id="post-text" name="text" rows="30" cols="100"></textarea>
        </li>
        <li><input type="submit" value="Submit"> </li>
    </ul>
</form>
</@layout.mainLayout>