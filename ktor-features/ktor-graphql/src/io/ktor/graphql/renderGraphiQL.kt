package io.ktor.graphql

const val GRAPHIQL_VERSION = "0.11.11"
const val JS_UNDEFINED = "undefined"

internal fun renderGraphiQL(data: Map<String, Any?>?, request: GraphQLRequest): String {

    val queryString = request.query

    val dataJSON = convertToJSONString(data)
    val variablesJSON = convertToJSONString(request.variables)

    val operationName = request.operationName

    val value =  """<!--
    The request to this GraphQL server provided the header "Accept: text/html"
    and as a result has been presented GraphiQL - an in-browser IDE for
        exploring GraphQL.
    If you wish to receive JSON, provide the header "Accept: application/json" or
    add "&raw" to the end of the URL within a browser.
    -->
    <!DOCTYPE html>
    <html>
    <head>
    <meta charset="utf-8" />
    <title>GraphiQL</title>
    <meta name="robots" content="noindex" />
    <meta name="referrer" content="origin" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
            body {
                margin: 0;
                overflow: hidden;
            }
    #graphiql {
        height: 100vh;
    }
    </style>
    <link href="//cdn.jsdelivr.net/npm/graphiql@${GRAPHIQL_VERSION}/graphiql.css" rel="stylesheet" />
    <script src="//cdn.jsdelivr.net/es6-promise/4.0.5/es6-promise.auto.min.js"></script>
    <script src="//cdn.jsdelivr.net/fetch/0.9.0/fetch.min.js"></script>
    <script src="//cdn.jsdelivr.net/react/15.4.2/react.min.js"></script>
    <script src="//cdn.jsdelivr.net/react/15.4.2/react-dom.min.js"></script>
    <script src="//cdn.jsdelivr.net/npm/graphiql@${GRAPHIQL_VERSION}/graphiql.min.js"></script>
    </head>
    <body>
    <div id="graphiql">Loading...</div>
    <script>
    // Collect the URL parameters
    var parameters = {};
    window.location.search.substr(1).split('&').forEach(function (entry) {
        var eq = entry.indexOf('=');
        if (eq >= 0) {
            parameters[decodeURIComponent(entry.slice(0, eq))] =
                    decodeURIComponent(entry.slice(eq + 1));
        }
    });
    // Produce a Location query string from a parameter object.
    function locationQuery(params) {
        return '?' + Object.keys(params).filter(function (key) {
            return Boolean(params[key]);
        }).map(function (key) {
            return encodeURIComponent(key) + '=' +
                    encodeURIComponent(params[key]);
        }).join('&');
    }
    // Derive a fetch URL from the current URL, sans the GraphQL parameters.
    var graphqlParamNames = {
        query: true,
        variables: true,
        operationName: true
    };
    var otherParams = {};
    for (var k in parameters) {
        if (parameters.hasOwnProperty(k) && graphqlParamNames[k] !== true) {
            otherParams[k] = parameters[k];
        }
    }
    var fetchURL = locationQuery(otherParams);
    // Defines a GraphQL fetcher using the fetch API.
    function graphQLFetcher(graphQLParams) {
        return fetch(fetchURL, {
            method: 'post',
            headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
            body: JSON.stringify(graphQLParams),
            credentials: 'include',
        }).then(function (response) {
            return response.json();
        });
    }
    // When the query and variables string is edited, update the URL bar so
    // that it can be easily shared.
    function onEditQuery(newQuery) {
        parameters.query = newQuery;
        updateURL();
    }
    function onEditVariables(newVariables) {
        parameters.variables = newVariables;
        updateURL();
    }
    function onEditOperationName(newOperationName) {
        parameters.operationName = newOperationName;
        updateURL();
    }
    function updateURL() {
        history.replaceState(null, null, locationQuery(parameters));
    }
    // Render <GraphiQL /> into the body.
    ReactDOM.render(
            React.createElement(GraphiQL, {
                fetcher: graphQLFetcher,
                onEditQuery: onEditQuery,
                onEditVariables: onEditVariables,
                onEditOperationName: onEditOperationName,
                query: ${serializeToJavascriptString(queryString)},
                response: ${serializeToJavascriptJson(dataJSON)},
                variables: ${serializeToJavascriptJson(variablesJSON)},
                operationName: ${serializeToJavascriptString(operationName)},
            }),
            document.getElementById('graphiql')
    );
    </script>
    </body>
    </html>"""

    return value
}

fun serializeToJavascriptString(data: String?): String {
    return if (data == null) {
        JS_UNDEFINED
    } else {
        val jsString =  mapper.writeValueAsString(data)
        safeSerialize(jsString)
    }
}

fun serializeToJavascriptJson(data: String?): String {
    return if (data == null) {
        JS_UNDEFINED
    } else {
        val safeData = safeSerialize(data)
        "JSON.stringify($safeData, null, 2)"
    }
}

fun safeSerialize(data: String) = data.replace("/", "\\/")

fun convertToJSONString(data: Map<String, Any?>?): String? {
    return if (data == null) {
        null
    } else {
        mapper.writeValueAsString(data)
    }
}