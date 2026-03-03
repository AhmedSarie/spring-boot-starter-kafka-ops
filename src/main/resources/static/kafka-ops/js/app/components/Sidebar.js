/* Consumer sidebar component — tree view with DLT/retry sub-topics */
var Sidebar = {
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
        var currentRoute = m.route.get() || '/';
        var routePrefix = currentRoute.indexOf('/browse') === 0 ? '/browse' : '/poll';

        function selectTopic(name) {
            AppState.selectedTopic = name;
            m.route.set(routePrefix, { topic: name });
        }

        function formatCount(count) {
            if (count === undefined || count === null) return '';
            if (count >= 1000000) return (count / 1000000).toFixed(1) + 'M';
            if (count >= 1000) return (count / 1000).toFixed(1) + 'K';
            return String(count);
        }

        function renderTopicItem(name, count, isActive, indent, extraContent) {
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
                m('.consumer-item-label', [
                    name,
                    (count !== undefined && count !== null)
                        ? m('span.consumer-count', ' (' + formatCount(count) + ')')
                        : null
                ]),
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
                }, [
                    dlt.name,
                    (dlt.messageCount !== undefined && dlt.messageCount !== null)
                        ? m('span.consumer-count', ' (' + formatCount(dlt.messageCount) + ')')
                        : null
                ]),
                m('button.btn.btn-sm.btn-drain-dlt[type=button]', {
                    title: 'Drain DLT — reprocess all messages',
                    onclick: function (e) {
                        e.stopPropagation();
                        Api.startDltRouting(mainTopicName).then(function (data) {
                            var id = data && data.id ? data.id.slice(0, 8) + '...' : '';
                            Toast.success('DLT drain started' + (id ? ' — ID: ' + id : ''));
                            /* Refresh sidebar to update counts */
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
                return renderTopicItem(consumer, null, isActive, false);
            }

            var items = [];
            var isMainActive = AppState.selectedTopic === consumer.name;

            /* Main topic */
            items.push(renderTopicItem(consumer.name, consumer.messageCount, isMainActive, false));

            /* DLT sub-topic */
            if (consumer.dlt) {
                items.push(renderDltItem(consumer.name, consumer.dlt));
            }

            /* Retry sub-topic */
            if (consumer.retry) {
                var isRetryActive = AppState.selectedTopic === consumer.retry.name;
                items.push(renderTopicItem(
                    consumer.retry.name, consumer.retry.messageCount, isRetryActive, true
                ));
            }

            return items;
        }

        /* Count total topics including sub-topics */
        var totalCount = 0;
        AppState.consumers.forEach(function (c) {
            if (typeof c === 'string') {
                totalCount++;
            } else {
                totalCount++;
                if (c.dlt) totalCount++;
                if (c.retry) totalCount++;
            }
        });

        return m('aside.sidebar', [
            m('.sidebar-header', m('h2', 'Kafka Consumers')),

            /* View toggle: Poll / Browse */
            !Api.disabled && AppState.consumers.length > 0
                ? m('.sidebar-view-toggle', [
                    m('button.btn-view-toggle' + (routePrefix === '/poll' ? '.active' : '') + '[type=button]', {
                        onclick: function () {
                            if (AppState.selectedTopic) {
                                m.route.set('/poll', { topic: AppState.selectedTopic });
                            } else {
                                m.route.set('/');
                            }
                        }
                    }, 'Poll'),
                    m('button.btn-view-toggle' + (routePrefix === '/browse' ? '.active' : '') + '[type=button]', {
                        onclick: function () {
                            if (AppState.selectedTopic) {
                                m.route.set('/browse', { topic: AppState.selectedTopic });
                            } else {
                                m.route.set('/browse');
                            }
                        }
                    }, 'Browse')
                ])
                : null,

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
            Api.disabled ? null : m('.sidebar-footer',
                m('p', AppState.consumers.length + ' registered consumer' + (AppState.consumers.length !== 1 ? 's' : ''))
            )
        ]);
    }
};
