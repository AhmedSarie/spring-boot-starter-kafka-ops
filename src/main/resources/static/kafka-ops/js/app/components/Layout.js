/* Shell layout: sidebar + header + content area */
var Layout = {
    view: function (vnode) {
        return m('.layout', [
            m(Sidebar),
            m('main.content', [
                m('header.header', [
                    m('.header-left', [
                        m.trust('<svg aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>'),
                        m('h1', 'Kafka Ops')
                    ])
                ]),
                m('.content-scroll',
                    m('.content-inner', vnode.children)
                )
            ]),
            m(Toast.component)
        ]);
    }
};
