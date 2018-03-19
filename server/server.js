var express = require("express");
var myParser = require("body-parser");
var app = express();

app.use(myParser.json());

var requestCount = 0;
var authRef = "12345-67890-abcdef";
app.post("/authentication/1.0/getOneResult", function (request, response) {
    console.log("Post Request received at : /authentication/1.0/getOneResult");
    var responseHeaders = getResponseHeaders(request, response);
    var data = {authRef: authRef};
    var status = 200;

    if(request.body.authRef!==authRef){
        status = 400;
        data = {message: "Invalid authRef"};
    }
    else if (requestCount >= 3) {
        data.status = "APPROVED";
        requestCount = 0;
    } else {
        requestCount++;
        data.status = "PENDING";
        status = 400;
    }

    response.writeHead(status, responseHeaders);
    response.end(JSON.stringify(data), 'utf-8');
});

app.post("/authentication/1.0/initAuthentication", function (request, response) {
    handleApiRequest(request, response);
});

var handleApiRequest = function (request, response) {

    console.log("Post Request received at : /authentication/1.0/initAuthentication");
    var responseHeaders = getResponseHeaders(request, response);
    if (!responseHeaders) {
        return;
    }
    var data = {};
    if (request.body.userInfo && request.body.userInfoType) {
        data = {authRef: authRef};
        response.writeHead(200, responseHeaders);
    } else {
        response.writeHead(400, responseHeaders);
    }
    response.end(JSON.stringify(data), 'utf-8');

    if (!response.finished) {
        console.log("handleApiRequest: Ending response");
    }
};

var getResponseHeaders = function (request, response) {
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
    return responseHeaders;
};

//Start the server and make it listen for connections on port 8100

app.listen(8200);
console.log('Server running at http://127.0.0.1:8200');