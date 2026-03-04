/* Route definitions + mount */
var root = document.getElementById('app');

m.route(root, '/', {
    '/': EmptyView,
    '/poll': PollView,
    '/browse': BrowseView
});
