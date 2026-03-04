/* Poll view — poll form + history + enriched JSON result + retry/correct actions */
var PollView = (function () {
    var state = {
        topic: '',
        partition: 0,
        offset: 0,
        message: null,       /* raw consumerRecordValue string */
        pollResponse: null,  /* full enriched response object */
        polling: false,
        retrying: false,
        showEditor: false,
        correctionValue: '',
        sending: false,
        jsonError: false,
        showDiffModal: false,
        headersExpanded: false
    };

    var anyBusy = function () { return state.polling || state.retrying || state.sending; };

    function getHistory() {
        return state.topic ? PollHistory.get(state.topic) : [];
    }

    function formatTimestamp(ts) {
        if (!ts) return '';
        try {
            return new Date(ts).toISOString().replace('T', ' ').replace('Z', ' UTC');
        } catch (e) {
            return String(ts);
        }
    }

    function pollMessage() {
        if (!state.topic) { Toast.error('No topic selected'); return; }
        if (state.partition < 0) { Toast.error('Partition must be >= 0'); return; }
        if (state.offset < 0) { Toast.error('Offset must be >= 0'); return; }

        state.polling = true;
        state.message = null;
        state.pollResponse = null;
        state.showEditor = false;
        state.headersExpanded = false;

        Api.poll(state.topic, state.partition, state.offset).then(function (data) {
            state.pollResponse = data;
            state.message = data.consumerRecordValue;
            if (state.message) {
                /* Use response partition/offset if available */
                if (data.partition !== undefined) state.partition = data.partition;
                if (data.offset !== undefined) state.offset = data.offset;
                PollHistory.add(state.topic, state.partition, state.offset);
                Toast.success('Message polled successfully');
            } else {
                Toast.error('No message found at this offset');
            }
        }).catch(function (e) {
            Toast.error('Poll failed: ' + Api.extractError(e));
        }).finally(function () {
            state.polling = false;
        });
    }

    function retryMessage() {
        state.retrying = true;
        Api.retry(state.topic, state.partition, state.offset).then(function (data) {
            var id = data && data.id ? data.id.slice(0, 8) + '...' : '';
            Toast.success('Retry queued' + (id ? ' — ID: ' + id : ''));
        }).catch(function (e) {
            Toast.error('Retry failed: ' + Api.extractError(e));
        }).finally(function () {
            state.retrying = false;
        });
    }

    function openEditor() {
        try {
            state.correctionValue = JSON.stringify(JSON.parse(state.message), null, 2);
        } catch (e) {
            state.correctionValue = state.message || '';
        }
        state.jsonError = false;
        state.showEditor = true;
        setTimeout(function () {
            var el = document.querySelector('.correction-editor');
            if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 50);
    }

    function closeEditor() {
        state.showEditor = false;
        state.jsonError = false;
    }

    function reviewCorrection() {
        var payload = state.correctionValue.trim();
        if (!payload) { Toast.error('Correction payload is empty'); return; }
        try {
            JSON.parse(payload);
            state.jsonError = false;
        } catch (e) {
            state.jsonError = true;
            return;
        }
        state.showDiffModal = true;
    }

    function confirmSendCorrection() {
        state.showDiffModal = false;
        state.sending = true;
        var payload = state.correctionValue.trim();
        Api.sendCorrection(state.topic, payload).then(function (data) {
            var id = data && data.id ? data.id.slice(0, 8) + '...' : '';
            Toast.success('Correction sent' + (id ? ' — ID: ' + id : ''));
            state.showEditor = false;
        }).catch(function (e) {
            Toast.error('Correction failed: ' + Api.extractError(e));
        }).finally(function () {
            state.sending = false;
        });
    }

    function cancelDiffModal() {
        state.showDiffModal = false;
    }

    function resetState(topic) {
        state.topic = topic;
        state.partition = 0;
        state.offset = 0;
        state.message = null;
        state.pollResponse = null;
        state.polling = false;
        state.retrying = false;
        state.showEditor = false;
        state.correctionValue = '';
        state.sending = false;
        state.jsonError = false;
        state.showDiffModal = false;
    }

    function selectHistory(entry) {
        state.partition = entry.partition;
        state.offset = entry.offset;
    }

    function renderMetadataBadges() {
        var resp = state.pollResponse;
        if (!resp) return null;

        var badges = [];
        if (resp.partition !== undefined) {
            badges.push(m('span.meta-badge', { key: 'p' }, 'P' + resp.partition));
        }
        if (resp.offset !== undefined) {
            badges.push(m('span.meta-badge', { key: 'o' }, 'O' + resp.offset));
        }
        if (resp.timestamp) {
            badges.push(m('span.meta-badge', { key: 'ts' }, formatTimestamp(resp.timestamp)));
        }
        if (resp.key) {
            badges.push(m('span.meta-badge.meta-badge-key', { key: 'k', title: resp.key },
                'Key: ' + (resp.key.length > 30 ? resp.key.slice(0, 30) + '...' : resp.key)));
        }
        return badges.length > 0 ? m('.result-metadata', badges) : null;
    }

    function renderHeaders() {
        var resp = state.pollResponse;
        if (!resp || !resp.headers) return null;
        var keys = Object.keys(resp.headers);
        if (keys.length === 0) return null;

        return m('.result-headers', [
            m('span.result-label.headers-toggle', {
                onclick: function () { state.headersExpanded = !state.headersExpanded; },
                style: 'cursor:pointer'
            }, 'Headers (' + keys.length + ') ' + (state.headersExpanded ? '\u25BC' : '\u25B6')),
            state.headersExpanded
                ? m('.headers-grid', keys.map(function (k) {
                    return m('.header-entry', { key: k }, [
                        m('span.header-key', k),
                        m('span.header-val', resp.headers[k])
                    ]);
                }))
                : null
        ]);
    }

    return {
        oninit: function () {
            var topic = m.route.param('topic') || AppState.selectedTopic || '';
            resetState(topic);
            AppState.selectedTopic = topic;
        },

        view: function () {
            var routeTopic = m.route.param('topic') || '';
            if (routeTopic && routeTopic !== state.topic) {
                resetState(routeTopic);
                AppState.selectedTopic = routeTopic;
            }

            var history = getHistory();

            var topicInfo = state.topic ? AppState.findTopicInfo(state.topic) : null;

            return m(Layout, [
                // Topic heading with metadata
                state.topic
                    ? m('.poll-topic-heading', [
                        state.topic,
                        topicInfo ? m('.topic-meta-badges', [
                            topicInfo.partitions !== undefined
                                ? m('span.meta-badge', topicInfo.partitions + ' partition' + (topicInfo.partitions !== 1 ? 's' : ''))
                                : null,
                            topicInfo.messageCount !== undefined
                                ? m('span.meta-badge', AppState.formatCount(topicInfo.messageCount) + ' messages')
                                : null
                        ]) : null
                    ])
                    : null,

                // Poll form (2-column: partition + offset)
                m('article', [
                    m('.form-grid-2', [
                        m('.form-group', [
                            m('label', { for: 'partition-input' }, 'Partition'),
                            m('input#partition-input', {
                                type: 'number',
                                value: state.partition,
                                min: 0,
                                placeholder: '0',
                                oninput: function (e) { state.partition = Number(e.target.value) || 0; },
                                onkeydown: function (e) { if (e.key === 'Enter') pollMessage(); }
                            })
                        ]),
                        m('.form-group', [
                            m('label', { for: 'offset-input' }, 'Offset'),
                            m('input#offset-input', {
                                type: 'number',
                                value: state.offset,
                                min: 0,
                                placeholder: '42',
                                oninput: function (e) { state.offset = Number(e.target.value) || 0; },
                                onkeydown: function (e) { if (e.key === 'Enter') pollMessage(); }
                            })
                        ])
                    ]),
                    m('button.btn.btn-primary.btn-full[type=button]', {
                        disabled: anyBusy() || !state.topic,
                        onclick: pollMessage
                    }, [
                        state.polling
                            ? m('span.spinner')
                            : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>'),
                        ' Poll Message'
                    ]),

                    // Recent history
                    history.length > 0 ? m('.poll-history', [
                        m('span.poll-history-label', 'Recent'),
                        m('.poll-history-list', history.map(function (entry) {
                            var isActive = entry.partition === state.partition && entry.offset === state.offset;
                            return m('button.poll-history-item' + (isActive ? '.active' : '') + '[type=button]', {
                                key: entry.partition + ':' + entry.offset,
                                onclick: function () { selectHistory(entry); }
                            }, 'P' + entry.partition + ' / O' + entry.offset);
                        }))
                    ]) : null
                ]),

                // Message result
                state.message ? m('.fade-in', { style: 'display:flex;flex-direction:column;gap:1rem' }, [
                    m('.result-header', [
                        m('span.result-label', 'Consumer Record Value'),
                        m('span.result-meta', 'P' + state.partition + ' / O' + state.offset)
                    ]),

                    /* Enriched metadata badges */
                    renderMetadataBadges(),

                    /* Headers */
                    renderHeaders(),

                    m(JsonViewer, { data: state.message }),
                    m('.action-buttons', [
                        m('button.btn.btn-secondary[type=button]', {
                            disabled: anyBusy(),
                            onclick: retryMessage
                        }, [
                            state.retrying
                                ? m('span.spinner')
                                : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>'),
                            ' Retry'
                        ]),
                        m('button.btn.btn-outline[type=button]', {
                            disabled: anyBusy(),
                            onclick: openEditor
                        }, [
                            m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>'),
                            ' Edit & Correct'
                        ])
                    ])
                ]) : null,

                // Correction editor
                state.showEditor && state.message ? m('.correction-editor.fade-in', {
                    onkeydown: function (e) { if (e.key === 'Escape') closeEditor(); }
                }, [
                    m('.correction-header', [
                        m('span', 'Edit & Correct — ' + state.topic),
                        m('button.btn-icon[type=button]', {
                            onclick: closeEditor,
                            'aria-label': 'Close'
                        }, m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>'))
                    ]),
                    m('textarea.correction-textarea', {
                        value: state.correctionValue,
                        oninput: function (e) { state.correctionValue = e.target.value; },
                        spellcheck: false
                    }),
                    state.jsonError ? m('.json-error', 'Invalid JSON — please check your syntax.') : null,
                    m('.correction-footer', [
                        m('button.btn.btn-primary.btn-sm[type=button]', {
                            disabled: anyBusy(),
                            onclick: reviewCorrection
                        }, [
                            state.sending
                                ? m('span.spinner')
                                : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>'),
                            ' Send Correction'
                        ])
                    ])
                ]) : null,

                // Empty state (no message polled yet)
                !state.message && !state.polling ? m('.empty-state', { style: 'padding:3rem 0' }, [
                    m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>'),
                    m('p', state.topic
                        ? 'Enter partition and offset to poll a message'
                        : 'Select a consumer from the sidebar to get started')
                ]) : null,

                // Diff confirmation modal
                state.showDiffModal ? m(DiffModal, {
                    original: state.message,
                    edited: state.correctionValue.trim(),
                    onConfirm: confirmSendCorrection,
                    onCancel: cancelDiffModal
                }) : null
            ]);
        }
    };
})();
