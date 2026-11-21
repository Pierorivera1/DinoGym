// CloudFront Function (viewer request). Every shared link, /r/<id>, is the
// same static page; the page reads the id from the address bar. The web
// dashboard's addresses (/, /history…, /progress…, /weight…) are one page too,
// which reads its view from the address bar (decision 112). Anything else goes
// to S3 or the API unchanged.
var DASHBOARD = /^\/(?:(?:history|progress|weight)(?:\/[^/]+)?\/?)?$/;

function handler(event) {
  var request = event.request;
  if (/^\/r\/[^/]+\/?$/.test(request.uri)) {
    request.uri = "/web/r.html";
  } else if (DASHBOARD.test(request.uri)) {
    request.uri = "/app/index.html";
  }
  return request;
}
