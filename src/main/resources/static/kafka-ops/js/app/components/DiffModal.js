/* Flat-diff confirmation modal for corrections */
var DiffModal = (function () {

    var previousFocus = null;

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

    /* Render changed/added/removed tables for a diff result */
    function renderDiffSection(diff) {
        var nodes = [];
        if (diff.changed.length > 0) {
            nodes.push(m('span.diff-section-label', 'Changed'));
            nodes.push(m('table.diff-table', [
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
            ]));
        }
        if (diff.added.length > 0) {
            nodes.push(m('span.diff-section-label', 'Added'));
            nodes.push(m('table.diff-table', [
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
            ]));
        }
        if (diff.removed.length > 0) {
            nodes.push(m('span.diff-section-label', 'Removed'));
            nodes.push(m('table.diff-table', [
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
            ]));
        }
        return nodes;
    }

    function closeAndRestoreFocus(onCancel) {
        onCancel();
        if (previousFocus) previousFocus.focus();
        previousFocus = null;
    }

    return {
        oninit: function () {
            previousFocus = document.activeElement;
        },

        oncreate: function (vnode) {
            var modal = vnode.dom.querySelector('.diff-modal');
            if (modal) {
                var focusable = modal.querySelectorAll('button, [tabindex]:not([tabindex="-1"])');
                if (focusable.length) focusable[0].focus();
            }
        },

        view: function (vnode) {
            var onConfirm = vnode.attrs.onConfirm;
            var onCancel = vnode.attrs.onCancel;

            /* Support both legacy (original/edited) and new (originalKey/editedKey + originalValue/editedValue) */
            var originalValue = vnode.attrs.originalValue !== undefined ? vnode.attrs.originalValue : vnode.attrs.original;
            var editedValue = vnode.attrs.editedValue !== undefined ? vnode.attrs.editedValue : vnode.attrs.edited;
            var originalKey = vnode.attrs.originalKey || null;
            var editedKey = vnode.attrs.editedKey || null;

            var valueDiff = computeDiff(originalValue, editedValue);
            var valueTotalChanges = valueDiff
                ? valueDiff.changed.length + valueDiff.added.length + valueDiff.removed.length
                : 0;

            var keyChanged = originalKey !== null && editedKey !== null && originalKey !== editedKey;
            var keyDiff = keyChanged ? computeDiff(originalKey, editedKey) : null;
            var keyTotalChanges = keyDiff
                ? keyDiff.changed.length + keyDiff.added.length + keyDiff.removed.length
                : 0;

            var hasAnyChanges = valueTotalChanges > 0 || keyTotalChanges > 0;

            return m('.diff-overlay', {
                role: 'dialog',
                'aria-modal': 'true',
                'aria-label': 'Correction diff review',
                onclick: function () { closeAndRestoreFocus(onCancel); }
            }, [
                m('.diff-modal', {
                    onclick: function (e) { e.stopPropagation(); },
                    onkeydown: function (e) {
                        if (e.key === 'Escape') {
                            closeAndRestoreFocus(onCancel);
                            return;
                        }
                        if (e.key === 'Tab') {
                            var modal = e.currentTarget;
                            var focusable = modal.querySelectorAll('button, [tabindex]:not([tabindex="-1"])');
                            if (focusable.length === 0) return;
                            var first = focusable[0];
                            var last = focusable[focusable.length - 1];
                            if (e.shiftKey && document.activeElement === first) {
                                e.preventDefault();
                                last.focus();
                            } else if (!e.shiftKey && document.activeElement === last) {
                                e.preventDefault();
                                first.focus();
                            }
                        }
                    }
                }, [
                    m('.diff-modal-header', 'Review Changes Before Sending'),

                    m('.diff-modal-body', [
                        !valueDiff ? m('p.diff-error', 'Unable to compare — invalid JSON.') : null,

                        valueDiff && !hasAnyChanges
                            ? m('p.diff-none', 'No changes detected.')
                            : null,

                        /* Key changes section (only shown if key was actually changed) */
                        keyDiff && keyTotalChanges > 0 ? [
                            m('span.diff-section-label', { style: 'font-weight:bold' }, 'Key Changes'),
                            renderDiffSection(keyDiff)
                        ] : null,

                        /* Value changes section */
                        valueDiff && valueTotalChanges > 0 ? [
                            keyDiff && keyTotalChanges > 0
                                ? m('span.diff-section-label', { style: 'font-weight:bold;margin-top:1rem;display:block' }, 'Value Changes')
                                : null,
                            renderDiffSection(valueDiff)
                        ] : null,

                        hasAnyChanges ? m('p.diff-summary', (function () {
                            var parts = [];
                            if (keyTotalChanges > 0) parts.push(keyTotalChanges + ' key field' + (keyTotalChanges !== 1 ? 's' : ''));
                            if (valueTotalChanges > 0) parts.push(valueTotalChanges + ' value field' + (valueTotalChanges !== 1 ? 's' : ''));
                            return parts.join(', ') + ' changed';
                        })()) : null
                    ]),

                    m('.diff-modal-footer', [
                        m('button.btn.btn-outline[type=button]', { onclick: onCancel }, 'Cancel'),
                        m('button.btn.btn-primary[type=button]', {
                            onclick: onConfirm,
                            disabled: !valueDiff
                        }, 'Confirm & Send')
                    ])
                ])
            ]);
        }
    };
})();
