/* Collapsible JSON tree viewer component */
var JsonViewer = (function () {
    var collapsed = {};

    function toggle(path) {
        collapsed[path] = !collapsed[path];
    }

    function renderValue(value, path, indent) {
        if (value === null) return m('span.json-null', 'null');
        if (typeof value === 'boolean') return m('span.json-boolean', String(value));
        if (typeof value === 'number') return m('span.json-number', String(value));
        if (typeof value === 'string') return m('span.json-string', '"' + escapeHtml(value) + '"');
        if (Array.isArray(value)) return renderArray(value, path, indent);
        if (typeof value === 'object') return renderObject(value, path, indent);
        return m('span', String(value));
    }

    function renderObject(obj, path, indent) {
        var keys = Object.keys(obj);
        if (keys.length === 0) return m('span.json-bracket', '{}');

        var hidden = !!collapsed[path];
        var next = indent + 1;
        var pad = spaces(next);
        var closePad = spaces(indent);

        return m('span', [
            m('span.json-toggle', { onclick: function () { toggle(path); } }, hidden ? '\u25B6 ' : '\u25BC '),
            m('span.json-bracket', '{'),
            hidden
                ? m('span.json-collapsed', { onclick: function () { toggle(path); } },
                    ' ... ' + keys.length + (keys.length === 1 ? ' key ' : ' keys '))
                : m('span', [
                    keys.map(function (key, i) {
                        return m('span', { key: key }, [
                            '\n' + pad,
                            m('span.json-key', '"' + escapeHtml(key) + '"'),
                            ': ',
                            renderValue(obj[key], path + '.' + key, next),
                            i < keys.length - 1 ? ',' : ''
                        ]);
                    }),
                    '\n' + closePad
                ]),
            m('span.json-bracket', '}')
        ]);
    }

    function renderArray(arr, path, indent) {
        if (arr.length === 0) return m('span.json-bracket', '[]');

        var hidden = !!collapsed[path];
        var next = indent + 1;
        var pad = spaces(next);
        var closePad = spaces(indent);

        return m('span', [
            m('span.json-toggle', { onclick: function () { toggle(path); } }, hidden ? '\u25B6 ' : '\u25BC '),
            m('span.json-bracket', '['),
            hidden
                ? m('span.json-collapsed', { onclick: function () { toggle(path); } },
                    ' ... ' + arr.length + (arr.length === 1 ? ' item ' : ' items '))
                : m('span', [
                    arr.map(function (item, i) {
                        return m('span', { key: String(i) }, [
                            '\n' + pad,
                            renderValue(item, path + '[' + i + ']', next),
                            i < arr.length - 1 ? ',' : ''
                        ]);
                    }),
                    '\n' + closePad
                ]),
            m('span.json-bracket', ']')
        ]);
    }

    function spaces(n) {
        var s = '';
        for (var i = 0; i < n; i++) s += '  ';
        return s;
    }

    function escapeHtml(str) {
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    return {
        oninit: function () {
            collapsed = {};
        },

        view: function (vnode) {
            var data = vnode.attrs.data;
            if (!data) return null;

            var parsed;
            try {
                parsed = JSON.parse(data);
            } catch (e) {
                return m('.json-viewer', m('pre', escapeHtml(data)));
            }

            return m('.json-viewer',
                m('pre', renderValue(parsed, '$', 0))
            );
        }
    };
})();
