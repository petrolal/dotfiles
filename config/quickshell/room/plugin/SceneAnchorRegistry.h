#pragma once

#include <QObject>
#include <QString>
#include <QStringList>
#include <QMap>
#include <QPointer>
#include <QtQuick3D/private/qquick3dnode_p.h>
#include <qqmlregistration.h>

class SceneAnchorRegistry : public QObject
{
    Q_OBJECT
    QML_ELEMENT

public:
    explicit SceneAnchorRegistry(QObject *parent = nullptr);

    Q_INVOKABLE bool registerAnchor(const QString &anchorId, QQuick3DNode *node);
    Q_INVOKABLE bool unregisterAnchor(const QString &anchorId);
    Q_INVOKABLE QQuick3DNode *anchorNode(const QString &anchorId) const;
    Q_INVOKABLE bool hasAnchor(const QString &anchorId) const;
    Q_INVOKABLE QStringList registeredAnchors() const;

signals:
    void anchorRegistered(const QString &anchorId, QQuick3DNode *node);
    void anchorUnregistered(const QString &anchorId);

private:
    QMap<QString, QPointer<QQuick3DNode>> m_anchors;
};
