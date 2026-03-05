/* Reusable themed confirmation modal — replaces window.confirm() */
var ConfirmModal = (function () {

    var previousFocus = null;

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
                if (focusable.length) focusable[focusable.length - 1].focus();
            }
        },

        /* attrs: { title, message, confirmLabel, onConfirm, onCancel } */
        view: function (vnode) {
            var title = vnode.attrs.title || 'Confirm';
            var message = vnode.attrs.message || 'Are you sure?';
            var confirmLabel = vnode.attrs.confirmLabel || 'Confirm';
            var onConfirm = vnode.attrs.onConfirm;
            var onCancel = vnode.attrs.onCancel;

            return m('.diff-overlay', {
                role: 'dialog',
                'aria-modal': 'true',
                'aria-label': title,
                onclick: function () { closeAndRestoreFocus(onCancel); }
            }, [
                m('.diff-modal', {
                    style: 'max-width: 28rem',
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
                    m('.diff-modal-header', title),
                    m('.diff-modal-body', [
                        m('p', { style: 'margin: 0; font-size: 0.875rem' }, message)
                    ]),
                    m('.diff-modal-footer', [
                        m('button.btn.btn-outline[type=button]', {
                            onclick: function () { closeAndRestoreFocus(onCancel); }
                        }, 'Cancel'),
                        m('button.btn.btn-primary[type=button]', {
                            onclick: onConfirm,
                            style: 'background: var(--kops-error); color: white'
                        }, confirmLabel)
                    ])
                ])
            ]);
        }
    };
})();
