var express = require("express");
var myParser = require("body-parser");
var app = express();

app.use(myParser.json());
app.post("/authorize", function (request, response) {
    handleApiRequest(request, response);
});

var handleApiRequest = function (request, response) {

    console.log("Serving API");

    var responseHeaders = {
        'Content-Type': 'application/json',
    };

    var origin = request.headers["origin"];

    if (request.method === 'OPTIONS') {
        console.log("Accepting probable preflight request");

        if (origin) {
            responseHeaders['Access-Control-Allow-Origin'] = origin;
            responseHeaders['Access-Control-Allow-Methods'] = "GET, HEAD, OPTIONS, POST";
            responseHeaders['Access-Control-Allow-Headers'] = 'Authorization, WWW-Authenticate, Content-Type';
            responseHeaders["Access-Control-Allow-Credentials"] = "true";
        }

        responseHeaders["Allow"] = "GET, HEAD, OPTIONS, POST";

        response.writeHead(200, responseHeaders);
        response.end();
        return;
    }
    else if (origin) {
        responseHeaders["Access-Control-Allow-Origin"] = origin;
        responseHeaders["Access-Control-Allow-Credentials"] = "true";
    }

    var data = {};
    if (request.body.userInfo && request.body.userInfoType) {
        data = {authRef: "Reference to be submitted in getAuthResults method"};
        response.writeHead(200, responseHeaders);
    } else {
        response.writeHead(400, responseHeaders);
    }
    response.end(JSON.stringify(data), 'utf-8');

    if (!response.finished) {
        console.log("handleApiRequest: Ending response");
    }
};

//Start the server and make it listen for connections on port 8100

app.listen(8200);
console.log('Server running at http://127.0.0.1:8200');