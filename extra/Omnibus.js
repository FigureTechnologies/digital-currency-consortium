const http = require("http");

const process = (request, response) => {
  setTimeout(() => {
    response.end();
  }, 100);
};

const server = http.createServer((request, response) => {
  const requestStart = Date.now();

  let body = [];
  let requestErrorMessage = null;

  const getChunk = chunk => body.push(chunk);
  const assembleBody = () => {
    body = Buffer.concat(body).toString();
  };
  const getError = error => {
    requestErrorMessage = error.message;
  };
  request.on("data", getChunk);
  request.on("end", assembleBody);
  request.on("error", getError);

  const logClose = () => {
    removeHandlers();
    log(request, response, "Client aborted.");
  };
  const logError = error => {
    removeHandlers();
    log(request, response, error.message);
  };
  const logFinish = () => {
    removeHandlers();
    log(request, response, requestErrorMessage);
  };
  response.on("close", logClose);
  response.on("error", logError);
  response.on("finish", logFinish);

  const removeHandlers = () => {
    request.off("data", getChunk);
    request.off("end", assembleBody);
    request.off("error", getError);
    response.off("close", logClose);
    response.off("error", logError);
    response.off("finish", logFinish);
  };

  const log = (request, response, errorMessage) => {
    const { rawHeaders, httpVersion, method, socket, url } = request;
    const { remoteAddress, remoteFamily } = socket;

    const { statusCode, statusMessage } = response;
    const headers = response.getHeaders();

    console.log(
      JSON.stringify({
        timestamp: Date.now(),
        processingTime: Date.now() - requestStart,
        rawHeaders,
        body,
        errorMessage,
        httpVersion,
        method,
        remoteAddress,
        remoteFamily,
        url,
        response: {
          statusCode,
          statusMessage,
          headers
        }
      })
    );
  };

  process(request, response);
});

server.listen(8888);
console.log("Omnibus server listening on port 8888")
