/* Consumer sidebar component — uses div elements to avoid Pico CSS interference */
var Sidebar = {
    oninit: function () {
        var load = Api.basePath ? Promise.resolve() : Api.init();
        load.then(function () {
            return Api.getConsumers();
        }).then(function (data) {
            AppState.consumers = Array.isArray(data) ? data.sort() : [];
            AppState.consumersLoading = false;
        }).catch(function (e) {
            var msg = Api.basePath ? 'Failed to load consumers' : 'Failed to load configuration';
            Toast.error(msg + ': ' + Api.extractError(e));
            AppState.consumersLoading = false;
        });
    },

    view: function () {
        return m('aside.sidebar', [
            m('.sidebar-header', m('h2', 'Kafka Consumers')),
            m('.sidebar-nav[role=listbox][aria-label=Consumer topics]',
                AppState.consumersLoading
                    ? m('.sidebar-loading', m('.spinner.spinner-sm'))
                    : AppState.consumers.length === 0
                        ? m('.sidebar-empty', 'No consumers registered')
                        : AppState.consumers.map(function (name) {
                            var isActive = AppState.selectedTopic === name;
                            return m('.consumer-item' + (isActive ? '.active' : ''), {
                                key: name,
                                role: 'option',
                                'aria-selected': isActive ? 'true' : 'false',
                                tabindex: '0',
                                title: name,
                                onclick: function () {
                                    AppState.selectedTopic = name;
                                    m.route.set('/poll', { topic: name });
                                },
                                onkeydown: function (e) {
                                    if (e.key === 'Enter' || e.key === ' ') {
                                        e.preventDefault();
                                        AppState.selectedTopic = name;
                                        m.route.set('/poll', { topic: name });
                                    }
                                }
                            }, [
                                m('.status-dot'),
                                m('.consumer-item-label', name)
                            ]);
                        })
            ),
            m('.sidebar-footer',
                m('p', AppState.consumers.length + ' registered topics')
            )
        ]);
    }
};
