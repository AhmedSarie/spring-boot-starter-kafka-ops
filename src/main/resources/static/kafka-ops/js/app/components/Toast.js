/* Toast notification system */
var Toast = (function () {
    var toasts = [];
    var nextId = 0;

    function show(message, type) {
        var id = nextId++;
        toasts.push({ id: id, message: message, type: type || 'success', exiting: false });
        m.redraw();
        setTimeout(function () { dismiss(id); }, 4000);
    }

    function dismiss(id) {
        var toast = toasts.find(function (t) { return t.id === id; });
        if (toast) {
            toast.exiting = true;
            m.redraw();
            setTimeout(function () {
                toasts = toasts.filter(function (t) { return t.id !== id; });
                m.redraw();
            }, 300);
        }
    }

    var component = {
        view: function () {
            return m('.toast-container', toasts.map(function (t) {
                return m('.toast.toast-' + t.type + (t.exiting ? '.toast-exit' : ''),
                    { key: t.id }, t.message);
            }));
        }
    };

    return {
        success: function (msg) { show(msg, 'success'); },
        error: function (msg) { show(msg, 'error'); },
        component: component
    };
})();
