/* Consumer sidebar component — tree view with collapsible DLT/retry sub-topics */
var Sidebar = {
    confirmDrain: null, /* { mainTopicName, dltName } when modal is open */
    expandedGroups: {},  /* { consumerName: true } — collapsed by default */

    oninit: function () {
        var load = Api.basePath ? Promise.resolve() : Api.init();
        load.then(function () {
            return Api.getConsumers();
        }).then(function (data) {
            if (Array.isArray(data)) {
                /* Sort by main topic name */
                AppState.consumers = data.sort(function (a, b) {
                    var nameA = (typeof a === 'string') ? a : (a.name || '');
                    var nameB = (typeof b === 'string') ? b : (b.name || '');
                    return nameA.localeCompare(nameB);
                });
            } else {
                AppState.consumers = [];
            }
            AppState.consumersLoading = false;
        }).catch(function (e) {
            AppState.consumersLoading = false;
        });
    },

    view: function () {
        function selectTopic(name) {
            AppState.selectedTopic = name;
            AppState.refreshConsumers();
            m.route.set('/browse', { topic: name });
        }

        function hasSubTopics(consumer) {
            return consumer.dlt || consumer.retry;
        }

        function isGroupExpanded(consumer) {
            /* Auto-expand if a child topic is active */
            if (consumer.dlt && AppState.selectedTopic === consumer.dlt.name) return true;
            if (consumer.retry && AppState.selectedTopic === consumer.retry.name) return true;
            return !!Sidebar.expandedGroups[consumer.name];
        }

        function renderTopicItem(name, isActive, indent, extraContent) {
            return m('.consumer-item' + (isActive ? '.active' : ''), {
                key: name,
                role: 'option',
                'aria-selected': isActive ? 'true' : 'false',
                tabindex: '0',
                title: name,
                style: indent ? 'padding-left: 2rem' : '',
                onclick: function () { selectTopic(name); },
                onkeydown: function (e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        selectTopic(name);
                    }
                }
            }, [
                m('.status-dot'),
                m('.consumer-item-label', name),
                extraContent || null
            ]);
        }

        function renderDltItem(mainTopicName, dlt) {
            var isActive = AppState.selectedTopic === dlt.name;
            return m('.consumer-item' + (isActive ? '.active' : ''), {
                key: dlt.name,
                role: 'option',
                'aria-selected': isActive ? 'true' : 'false',
                tabindex: '0',
                title: dlt.name,
                style: 'padding-left: 2rem'
            }, [
                m('.status-dot', { style: 'background: var(--kops-error)' }),
                m('.consumer-item-label', {
                    onclick: function (e) {
                        e.stopPropagation();
                        selectTopic(dlt.name);
                    }
                }, dlt.name),
                m('button.btn.btn-sm.btn-drain-dlt[type=button]', {
                    title: 'Drain DLT — reprocess all messages',
                    onclick: function (e) {
                        e.stopPropagation();
                        Sidebar.confirmDrain = { mainTopicName: mainTopicName, dltName: dlt.name };
                    }
                }, [
                    m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>'),
                    ' Drain'
                ])
            ]);
        }

        function renderConsumerTree(consumer) {
            /* Support legacy string format as fallback */
            if (typeof consumer === 'string') {
                var isActive = AppState.selectedTopic === consumer;
                return renderTopicItem(consumer, isActive, false);
            }

            var items = [];
            var isMainActive = AppState.selectedTopic === consumer.name;
            var hasSubs = hasSubTopics(consumer);
            var expanded = hasSubs && isGroupExpanded(consumer);

            /* Main topic — click selects + toggles expand/collapse */
            items.push(m('.consumer-item' + (isMainActive ? '.active' : ''), {
                key: consumer.name,
                role: 'option',
                'aria-selected': isMainActive ? 'true' : 'false',
                tabindex: '0',
                title: consumer.name,
                onclick: function () {
                    selectTopic(consumer.name);
                    if (hasSubs) {
                        Sidebar.expandedGroups[consumer.name] = !Sidebar.expandedGroups[consumer.name];
                    }
                },
                onkeydown: function (e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        selectTopic(consumer.name);
                        if (hasSubs) {
                            Sidebar.expandedGroups[consumer.name] = !Sidebar.expandedGroups[consumer.name];
                        }
                    }
                }
            }, [
                m('.status-dot'),
                m('.consumer-item-label', consumer.name),
                hasSubs ? m('span.consumer-toggle', expanded ? '\u25BC' : '\u25B6') : null
            ]));

            /* DLT and retry sub-topics (only when expanded) */
            if (expanded) {
                if (consumer.dlt) {
                    items.push(renderDltItem(consumer.name, consumer.dlt));
                }
                if (consumer.retry) {
                    var isRetryActive = AppState.selectedTopic === consumer.retry.name;
                    items.push(renderTopicItem(
                        consumer.retry.name, isRetryActive, true
                    ));
                }
            }

            return items;
        }

        return m('aside.sidebar', [
            m('.sidebar-header', m('h2', 'Kafka consumers')),

            m('.sidebar-nav[role=listbox][aria-label=Consumer topics]',
                Api.disabled
                    ? m('.sidebar-disabled', [
                        m('p', 'Console is not enabled.'),
                        m('p', 'Set the following in your application configuration:'),
                        m('code', 'kafka.ops.console.enabled=true'),
                        m('br'),
                        m('code', 'kafka.ops.rest-api.enabled=true')
                    ])
                    : AppState.consumersLoading
                        ? m('.sidebar-loading', m('.spinner.spinner-sm'))
                        : AppState.consumers.length === 0
                            ? m('.sidebar-empty', 'No consumers registered')
                            : AppState.consumers.map(function (consumer) {
                                return renderConsumerTree(consumer);
                            })
            ),
            Api.disabled ? null : m('.sidebar-footer', [
                m('p', AppState.consumers.length + ' registered consumer' + (AppState.consumers.length !== 1 ? 's' : '')),
                AppState.dltRouting && AppState.dltRouting.enabled
                    ? m('.dlt-config-info', [
                        m('span.dlt-config-label', 'DLT routing'),
                        m('span.dlt-config-item', 'Cron: ' + AppState.dltRouting.restartCron),
                        m('span.dlt-config-item', 'Max cycles: ' + AppState.dltRouting.maxCycles),
                        m('span.dlt-config-item', 'Idle stop: ' + AppState.dltRouting.idleShutdownSeconds + 's')
                    ])
                    : null
            ]),

            Sidebar.confirmDrain ? m(ConfirmModal, {
                title: 'Drain DLT',
                message: 'Are you sure you want to drain DLT messages for "' + Sidebar.confirmDrain.dltName + '" back to the retry topic?',
                confirmLabel: 'Drain',
                onCancel: function () { Sidebar.confirmDrain = null; },
                onConfirm: function () {
                    var topic = Sidebar.confirmDrain.mainTopicName;
                    Sidebar.confirmDrain = null;
                    Api.startDltRouting(topic).then(function (data) {
                        var id = data && data.id ? data.id.slice(0, 8) + '...' : '';
                        Toast.success('DLT drain started' + (id ? ' — ID: ' + id : ''));
                        Api.getConsumers().then(function (d) {
                            if (Array.isArray(d)) {
                                AppState.consumers = d.sort(function (a, b) {
                                    var nameA = (typeof a === 'string') ? a : (a.name || '');
                                    var nameB = (typeof b === 'string') ? b : (b.name || '');
                                    return nameA.localeCompare(nameB);
                                });
                            }
                        });
                    }).catch(function (err) {
                        Toast.error('DLT drain failed: ' + Api.extractError(err));
                    });
                }
            }) : null
        ]);
    }
};
