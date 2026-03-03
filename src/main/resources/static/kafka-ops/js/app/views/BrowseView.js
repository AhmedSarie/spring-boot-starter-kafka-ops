/* Browse view — batch message browsing with filter, expand, retry/correct */
var BrowseView = (function () {
    var state = {
        topic: '',
        mode: 'timestamp',   /* 'timestamp' or 'offset' */
        startTimestamp: '',   /* datetime-local string */
        partition: 0,
        startOffset: 0,
        limit: 20,
        records: [],
        hasMore: false,
        loading: false,
        filter: '',
        expandedIndex: -1,
        /* Per-expanded-row actions */
        retrying: false,
        showEditor: false,
        correctionValue: '',
        sending: false,
        jsonError: false,
        showDiffModal: false
    };

    var anyBusy = function () { return state.loading || state.retrying || state.sending; };

    function formatTimestamp(ts) {
        if (!ts) return '';
        try {
            return new Date(ts).toISOString().replace('T', ' ').replace('Z', ' UTC');
        } catch (e) {
            return String(ts);
        }
    }

    function truncate(str, max) {
        max = max || 60;
        if (!str) return '';
        return str.length > max ? str.slice(0, max) + '...' : str;
    }

    function datetimeToEpoch(dtStr) {
        if (!dtStr) return null;
        var d = new Date(dtStr);
        return isNaN(d.getTime()) ? null : d.getTime();
    }

    function buildParams(append) {
        var params = { limit: state.limit };

        if (append && state.records.length > 0) {
            /* Load more: use last record's partition/offset+1 */
            var last = state.records[state.records.length - 1];
            params.partition = last.partition;
            params.startOffset = last.offset + 1;
            return params;
        }

        if (state.mode === 'timestamp') {
            var epoch = datetimeToEpoch(state.startTimestamp);
            if (!epoch) return null;
            params.startTimestamp = epoch;
        } else {
            params.partition = state.partition;
            params.startOffset = state.startOffset;
        }
        return params;
    }

    function fetchRecords(append) {
        if (!state.topic) { Toast.error('No topic selected'); return; }

        var params = buildParams(append);
        if (!params) {
            Toast.error('Please enter a valid ' + (state.mode === 'timestamp' ? 'timestamp' : 'offset'));
            return;
        }

        state.loading = true;
        if (!append) {
            state.records = [];
            state.expandedIndex = -1;
            state.showEditor = false;
        }

        Api.batchPoll(state.topic, params).then(function (data) {
            if (append) {
                state.records = state.records.concat(data.records || []);
            } else {
                state.records = data.records || [];
            }
            state.hasMore = !!data.hasMore;
            if (state.records.length === 0) {
                Toast.error('No messages found');
            } else {
                Toast.success(data.records.length + ' message' + (data.records.length !== 1 ? 's' : '') + ' loaded');
            }
        }).catch(function (e) {
            Toast.error('Batch poll failed: ' + Api.extractError(e));
        }).finally(function () {
            state.loading = false;
        });
    }

    function matchesFilter(record) {
        if (!state.filter) return true;
        var q = state.filter.toLowerCase();
        var searchable = [
            String(record.partition),
            String(record.offset),
            formatTimestamp(record.timestamp),
            record.key || '',
            record.value || ''
        ].join(' ').toLowerCase();
        return searchable.indexOf(q) !== -1;
    }

    function expandRow(index) {
        if (state.expandedIndex === index) {
            state.expandedIndex = -1;
            state.showEditor = false;
        } else {
            state.expandedIndex = index;
            state.showEditor = false;
            state.jsonError = false;
        }
    }

    function retryRecord(record) {
        state.retrying = true;
        Api.retry(state.topic, record.partition, record.offset).then(function (data) {
            var id = data && data.id ? data.id.slice(0, 8) + '...' : '';
            Toast.success('Retry queued' + (id ? ' — ID: ' + id : ''));
        }).catch(function (e) {
            Toast.error('Retry failed: ' + Api.extractError(e));
        }).finally(function () {
            state.retrying = false;
        });
    }

    function openEditorForRecord(record) {
        try {
            state.correctionValue = JSON.stringify(JSON.parse(record.value), null, 2);
        } catch (e) {
            state.correctionValue = record.value || '';
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
        state.mode = 'timestamp';
        state.startTimestamp = '';
        state.partition = 0;
        state.startOffset = 0;
        state.limit = 20;
        state.records = [];
        state.hasMore = false;
        state.loading = false;
        state.filter = '';
        state.expandedIndex = -1;
        state.retrying = false;
        state.showEditor = false;
        state.correctionValue = '';
        state.sending = false;
        state.jsonError = false;
        state.showDiffModal = false;
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

            var filteredRecords = state.records.filter(matchesFilter);
            var expandedRecord = (state.expandedIndex >= 0 && state.expandedIndex < filteredRecords.length)
                ? filteredRecords[state.expandedIndex]
                : null;

            return m(Layout, { wide: true }, [
                // Topic heading
                state.topic
                    ? m('.poll-topic-heading', state.topic)
                    : null,

                // Browse form
                m('article', [
                    /* Mode toggle */
                    m('.browse-mode-toggle', [
                        m('button.btn.btn-sm' + (state.mode === 'timestamp' ? '.btn-primary' : '.btn-outline') + '[type=button]', {
                            onclick: function () { state.mode = 'timestamp'; }
                        }, 'By Timestamp'),
                        m('button.btn.btn-sm' + (state.mode === 'offset' ? '.btn-primary' : '.btn-outline') + '[type=button]', {
                            onclick: function () { state.mode = 'offset'; }
                        }, 'By Offset')
                    ]),

                    state.mode === 'timestamp'
                        ? m('.form-grid-2', [
                            m('.form-group', [
                                m('label', { for: 'browse-ts-input' }, 'Start Timestamp'),
                                m('input#browse-ts-input', {
                                    type: 'datetime-local',
                                    step: 1,
                                    value: state.startTimestamp,
                                    oninput: function (e) { state.startTimestamp = e.target.value; },
                                    onkeydown: function (e) { if (e.key === 'Enter') fetchRecords(false); }
                                })
                            ]),
                            m('.form-group', [
                                m('label', { for: 'browse-limit-ts' }, 'Limit'),
                                m('input#browse-limit-ts', {
                                    type: 'number',
                                    value: state.limit,
                                    min: 1,
                                    max: 100,
                                    placeholder: '20',
                                    oninput: function (e) { state.limit = Number(e.target.value) || 20; },
                                    onkeydown: function (e) { if (e.key === 'Enter') fetchRecords(false); }
                                })
                            ])
                        ])
                        : m('.browse-offset-form', [
                            m('.form-group', [
                                m('label', { for: 'browse-part-input' }, 'Partition'),
                                m('input#browse-part-input', {
                                    type: 'number',
                                    value: state.partition,
                                    min: 0,
                                    placeholder: '0',
                                    oninput: function (e) { state.partition = Number(e.target.value) || 0; },
                                    onkeydown: function (e) { if (e.key === 'Enter') fetchRecords(false); }
                                })
                            ]),
                            m('.form-group', [
                                m('label', { for: 'browse-offset-input' }, 'Start Offset'),
                                m('input#browse-offset-input', {
                                    type: 'number',
                                    value: state.startOffset,
                                    min: 0,
                                    placeholder: '0',
                                    oninput: function (e) { state.startOffset = Number(e.target.value) || 0; },
                                    onkeydown: function (e) { if (e.key === 'Enter') fetchRecords(false); }
                                })
                            ]),
                            m('.form-group', [
                                m('label', { for: 'browse-limit-off' }, 'Limit'),
                                m('input#browse-limit-off', {
                                    type: 'number',
                                    value: state.limit,
                                    min: 1,
                                    max: 100,
                                    placeholder: '20',
                                    oninput: function (e) { state.limit = Number(e.target.value) || 20; },
                                    onkeydown: function (e) { if (e.key === 'Enter') fetchRecords(false); }
                                })
                            ])
                        ]),

                    m('button.btn.btn-primary.btn-full[type=button]', {
                        disabled: anyBusy() || !state.topic,
                        onclick: function () { fetchRecords(false); }
                    }, [
                        state.loading && state.records.length === 0
                            ? m('span.spinner')
                            : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/></svg>'),
                        ' Browse Messages'
                    ])
                ]),

                // Results
                state.records.length > 0 ? m('.fade-in', { style: 'display:flex;flex-direction:column;gap:1rem' }, [
                    // Filter + count header
                    m('.browse-results-header', [
                        m('.form-group', { style: 'flex:1;margin:0' }, [
                            m('input.browse-filter-input', {
                                type: 'text',
                                placeholder: 'Filter results...',
                                value: state.filter,
                                oninput: function (e) {
                                    state.filter = e.target.value;
                                    state.expandedIndex = -1;
                                    state.showEditor = false;
                                }
                            })
                        ]),
                        m('span.result-meta', filteredRecords.length + ' of ' + state.records.length + ' shown')
                    ]),

                    // Results table
                    m('.browse-table-container', [
                        m('table.browse-table', [
                            m('thead', m('tr', [
                                m('th', 'Partition'),
                                m('th', 'Offset'),
                                m('th', 'Timestamp'),
                                m('th', 'Key'),
                                m('th', 'Value')
                            ])),
                            m('tbody', filteredRecords.map(function (record, i) {
                                var isExpanded = state.expandedIndex === i;
                                var rows = [];

                                rows.push(m('tr.browse-row' + (isExpanded ? '.browse-row-active' : ''), {
                                    key: record.partition + ':' + record.offset,
                                    onclick: function () { expandRow(i); }
                                }, [
                                    m('td', record.partition),
                                    m('td', record.offset),
                                    m('td', formatTimestamp(record.timestamp)),
                                    m('td.browse-cell-truncate', { title: record.key || '' }, truncate(record.key, 30)),
                                    m('td.browse-cell-truncate', { title: record.value || '' }, truncate(record.value, 50))
                                ]));

                                if (isExpanded) {
                                    rows.push(m('tr.browse-expanded-row', {
                                        key: record.partition + ':' + record.offset + ':expanded'
                                    }, [
                                        m('td', { colspan: 5 }, [
                                            /* Metadata badges */
                                            m('.result-metadata', [
                                                m('span.meta-badge', 'P' + record.partition),
                                                m('span.meta-badge', 'O' + record.offset),
                                                record.timestamp ? m('span.meta-badge', formatTimestamp(record.timestamp)) : null,
                                                record.key ? m('span.meta-badge.meta-badge-key', { title: record.key },
                                                    'Key: ' + truncate(record.key, 30)) : null
                                            ]),

                                            /* Headers */
                                            record.headers && Object.keys(record.headers).length > 0
                                                ? m('.result-headers', [
                                                    m('span.result-label', 'Headers'),
                                                    m('.headers-grid', Object.keys(record.headers).map(function (k) {
                                                        return m('.header-entry', { key: k }, [
                                                            m('span.header-key', k),
                                                            m('span.header-val', record.headers[k])
                                                        ]);
                                                    }))
                                                ])
                                                : null,

                                            /* Full JSON value */
                                            m(JsonViewer, { data: record.value }),

                                            /* Action buttons */
                                            m('.action-buttons', { style: 'margin-top:0.75rem' }, [
                                                m('button.btn.btn-secondary.btn-sm[type=button]', {
                                                    disabled: anyBusy(),
                                                    onclick: function (e) {
                                                        e.stopPropagation();
                                                        retryRecord(record);
                                                    }
                                                }, [
                                                    state.retrying
                                                        ? m('span.spinner')
                                                        : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>'),
                                                    ' Retry'
                                                ]),
                                                m('button.btn.btn-outline.btn-sm[type=button]', {
                                                    disabled: anyBusy(),
                                                    onclick: function (e) {
                                                        e.stopPropagation();
                                                        openEditorForRecord(record);
                                                    }
                                                }, [
                                                    m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>'),
                                                    ' Edit & Correct'
                                                ])
                                            ]),

                                            /* Correction editor inline */
                                            state.showEditor ? m('.correction-editor.fade-in', {
                                                style: 'margin-top:0.75rem',
                                                onclick: function (e) { e.stopPropagation(); },
                                                onkeydown: function (e) { if (e.key === 'Escape') closeEditor(); }
                                            }, [
                                                m('.correction-header', [
                                                    m('span', 'Edit & Correct — ' + state.topic),
                                                    m('button.btn-icon[type=button]', {
                                                        onclick: function (e) {
                                                            e.stopPropagation();
                                                            closeEditor();
                                                        },
                                                        'aria-label': 'Close'
                                                    }, m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>'))
                                                ]),
                                                m('textarea.correction-textarea', {
                                                    value: state.correctionValue,
                                                    oninput: function (e) { state.correctionValue = e.target.value; },
                                                    spellcheck: false,
                                                    onclick: function (e) { e.stopPropagation(); }
                                                }),
                                                state.jsonError ? m('.json-error', 'Invalid JSON — please check your syntax.') : null,
                                                m('.correction-footer', [
                                                    m('button.btn.btn-primary.btn-sm[type=button]', {
                                                        disabled: anyBusy(),
                                                        onclick: function (e) {
                                                            e.stopPropagation();
                                                            reviewCorrection();
                                                        }
                                                    }, [
                                                        state.sending
                                                            ? m('span.spinner')
                                                            : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>'),
                                                        ' Send Correction'
                                                    ])
                                                ])
                                            ]) : null
                                        ])
                                    ]));
                                }

                                return rows;
                            }))
                        ])
                    ]),

                    // Load more button
                    state.hasMore ? m('button.btn.btn-outline.btn-full[type=button]', {
                        disabled: state.loading,
                        onclick: function () { fetchRecords(true); }
                    }, [
                        state.loading
                            ? m('span.spinner')
                            : m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="7 13 12 18 17 13"/><polyline points="7 6 12 11 17 6"/></svg>'),
                        ' Load More'
                    ]) : null
                ]) : null,

                // Empty state
                state.records.length === 0 && !state.loading ? m('.empty-state', { style: 'padding:3rem 0' }, [
                    m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="3" y1="15" x2="21" y2="15"/><line x1="9" y1="3" x2="9" y2="21"/></svg>'),
                    m('p', state.topic
                        ? 'Enter a timestamp or offset range to browse messages'
                        : 'Select a consumer from the sidebar to get started')
                ]) : null,

                // Diff confirmation modal
                state.showDiffModal && expandedRecord ? m(DiffModal, {
                    original: expandedRecord.value,
                    edited: state.correctionValue.trim(),
                    onConfirm: confirmSendCorrection,
                    onCancel: cancelDiffModal
                }) : null
            ]);
        }
    };
})();
