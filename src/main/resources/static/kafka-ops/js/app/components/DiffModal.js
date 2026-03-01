/* Flat-diff confirmation modal for corrections */
var DiffModal = (function () {

    /* Flatten a JSON object to dot-notation key-value pairs */
    function flatten(obj, prefix) {
        var result = {};
        prefix = prefix || '';
        if (obj === null || typeof obj !== 'object') {
            result[prefix || '$'] = obj;
            return result;
        }
        if (Array.isArray(obj)) {
            if (obj.length === 0) {
                result[prefix] = '[]';
            } else {
                obj.forEach(function (item, i) {
                    var key = prefix + '[' + i + ']';
                    var sub = flatten(item, key);
                    Object.keys(sub).forEach(function (k) { result[k] = sub[k]; });
                });
            }
            return result;
        }
        var keys = Object.keys(obj);
        if (keys.length === 0) {
            result[prefix] = '{}';
        } else {
            keys.forEach(function (key) {
                var path = prefix ? prefix + '.' + key : key;
                var sub = flatten(obj[key], path);
                Object.keys(sub).forEach(function (k) { result[k] = sub[k]; });
            });
        }
        return result;
    }

    function formatValue(v) {
        if (v === null) return 'null';
        if (v === undefined) return 'undefined';
        if (typeof v === 'string') return '"' + v + '"';
        return String(v);
    }

    function truncate(str, max) {
        max = max || 60;
        return str.length > max ? str.slice(0, max) + '...' : str;
    }

    /* Compute diff between original and edited JSON strings */
    function computeDiff(originalStr, editedStr) {
        var original, edited;
        try { original = JSON.parse(originalStr); } catch (e) { return null; }
        try { edited = JSON.parse(editedStr); } catch (e) { return null; }

        var flatOrig = flatten(original, '');
        var flatEdit = flatten(edited, '');
        var allKeys = {};
        Object.keys(flatOrig).forEach(function (k) { allKeys[k] = true; });
        Object.keys(flatEdit).forEach(function (k) { allKeys[k] = true; });

        var changed = [];
        var added = [];
        var removed = [];

        Object.keys(allKeys).sort().forEach(function (key) {
            var inOrig = flatOrig.hasOwnProperty(key);
            var inEdit = flatEdit.hasOwnProperty(key);
            if (inOrig && inEdit) {
                var origVal = formatValue(flatOrig[key]);
                var editVal = formatValue(flatEdit[key]);
                if (origVal !== editVal) {
                    changed.push({ field: key, was: origVal, now: editVal });
                }
            } else if (inOrig && !inEdit) {
                removed.push({ field: key, was: formatValue(flatOrig[key]) });
            } else {
                added.push({ field: key, now: formatValue(flatEdit[key]) });
            }
        });

        return { changed: changed, added: added, removed: removed };
    }

    return {
        view: function (vnode) {
            var original = vnode.attrs.original;
            var edited = vnode.attrs.edited;
            var onConfirm = vnode.attrs.onConfirm;
            var onCancel = vnode.attrs.onCancel;

            var diff = computeDiff(original, edited);
            var totalChanges = diff
                ? diff.changed.length + diff.added.length + diff.removed.length
                : 0;

            return m('.diff-overlay', { onclick: onCancel }, [
                m('.diff-modal', {
                    onclick: function (e) { e.stopPropagation(); },
                    onkeydown: function (e) { if (e.key === 'Escape') onCancel(); }
                }, [
                    m('.diff-modal-header', 'Review Changes Before Sending'),

                    m('.diff-modal-body', [
                        !diff ? m('p.diff-error', 'Unable to compare — invalid JSON.') : null,

                        diff && totalChanges === 0
                            ? m('p.diff-none', 'No changes detected.')
                            : null,

                        diff && diff.changed.length > 0 ? [
                            m('span.diff-section-label', 'Changed'),
                            m('table.diff-table', [
                                m('thead', m('tr', [
                                    m('th', 'Field'),
                                    m('th', 'Was'),
                                    m('th', 'Now')
                                ])),
                                m('tbody', diff.changed.map(function (d) {
                                    return m('tr', { key: d.field }, [
                                        m('td.diff-field', d.field),
                                        m('td.diff-was', truncate(d.was)),
                                        m('td.diff-now', truncate(d.now))
                                    ]);
                                }))
                            ])
                        ] : null,

                        diff && diff.added.length > 0 ? [
                            m('span.diff-section-label', 'Added'),
                            m('table.diff-table', [
                                m('thead', m('tr', [
                                    m('th', 'Field'),
                                    m('th', 'Value')
                                ])),
                                m('tbody', diff.added.map(function (d) {
                                    return m('tr', { key: d.field }, [
                                        m('td.diff-field', d.field),
                                        m('td.diff-now', truncate(d.now))
                                    ]);
                                }))
                            ])
                        ] : null,

                        diff && diff.removed.length > 0 ? [
                            m('span.diff-section-label', 'Removed'),
                            m('table.diff-table', [
                                m('thead', m('tr', [
                                    m('th', 'Field'),
                                    m('th', 'Value')
                                ])),
                                m('tbody', diff.removed.map(function (d) {
                                    return m('tr', { key: d.field }, [
                                        m('td.diff-field', d.field),
                                        m('td.diff-was', truncate(d.was))
                                    ]);
                                }))
                            ])
                        ] : null,

                        diff && totalChanges > 0 ? m('p.diff-summary', [
                            diff.changed.length > 0 ? diff.changed.length + ' changed' : '',
                            diff.changed.length > 0 && (diff.added.length > 0 || diff.removed.length > 0) ? ', ' : '',
                            diff.added.length > 0 ? diff.added.length + ' added' : '',
                            diff.added.length > 0 && diff.removed.length > 0 ? ', ' : '',
                            diff.removed.length > 0 ? diff.removed.length + ' removed' : ''
                        ].join('')) : null
                    ]),

                    m('.diff-modal-footer', [
                        m('button.btn.btn-outline[type=button]', { onclick: onCancel }, 'Cancel'),
                        m('button.btn.btn-primary[type=button]', {
                            onclick: onConfirm,
                            disabled: !diff
                        }, 'Confirm & Send')
                    ])
                ])
            ]);
        }
    };
})();
