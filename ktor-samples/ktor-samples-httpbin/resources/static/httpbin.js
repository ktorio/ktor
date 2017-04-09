var base = "http://localhost:8080/";
$(document).ready(function () {
    link("view_ip", "/ip");
    link("view_user_agent", "/user-agent");
    link("view_headers", "/headers");
    link("view_get", "/get?id=1&gender=MALE");
    link("view_gzip_encoded_content", "/gzip");
    link("view_deflate_encoded_content", "/deflate");
    link("response_headers", "/response-headers?X-Requested-With=Postman&Cache-Control=private");
    link("redirect_n_times", "/redirect/6");
    link("view_cookies", "/cookies");
    link("set_cookies", "/cookies?set?k1=v1&k2=v2");
    link("delete_cookies", "/cookies?delete?k1&k2");

    linkMethod("view_post", "POST", "/post");
    linkMethod("view_patch", "PATCH", "/patch");
    linkMethod("view_put", "PUT", "/put");
    linkMethod("view_delete", "DELETE", "/delete");

});

function linkMethod(id, method, url) {
    var link = $("#" + id);
    link.click(function () {

        $.ajax({
            url: base + url,
            method: method,
            data: {"comments": "cheese was delicious", "custname": "John Doe"},
            contentType: "application/json",
            dataType: "json"
        }).done(function (msg) {
            show(msg);
        });
    });
}

function link(id, url) {
    var link = $("#" + id);
    link.href = "#";
    link.click(function () {
        $.getJSON(base + url, null, show);
    });
}
function show(json) {
    alert(JSON.stringify(json, null, 2));
}

$(document).ready(function () {
    var codes = $('.fetch');
    codes.each(function () {
        var code = $(this);
        var url = code.attr('url');
        console.info(url);
        $.ajax({
            url: base + url,
            contentType: "application/json",
            dataType: "json"
        }).done(function (msg) {
            var string = JSON.stringify(msg, null, 2)
            console.info(string);
            code.text(string);
        });

    });
});