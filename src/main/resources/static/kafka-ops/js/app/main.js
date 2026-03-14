/* Route definitions + mount */
var root = document.getElementById('app');

m.route(root, '/', {
    '/': EmptyView,
    '/browse': BrowseView
});
