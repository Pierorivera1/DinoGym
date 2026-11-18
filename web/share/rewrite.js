// CloudFront Function (viewer request). Every shared link, /r/<id>, is the
// same static page; the page reads the id from the address bar. Anything else
// goes to S3 unchanged.
function handler(event) {
  var request = event.request;
  if (/^\/r\/[^/]+\/?$/.test(request.uri)) {
    request.uri = "/web/r.html";
  }
  return request;
}
