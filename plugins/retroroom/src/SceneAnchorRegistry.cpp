#include "SceneAnchorRegistry.h"
#include <QDebug>

SceneAnchorRegistry::SceneAnchorRegistry(QObject *parent)
    : QObject(parent)
{
}

bool SceneAnchorRegistry::registerAnchor(const QString &anchorId, QQuick3DNode *node)
{
    if (anchorId.isEmpty() || !node) {
        qWarning() << "[SceneAnchorRegistry] Invalid anchor registration:" << anchorId << node;
        return false;
    }

    m_anchors.insert(anchorId, node);
    emit anchorRegistered(anchorId, node);
    return true;
}

bool SceneAnchorRegistry::unregisterAnchor(const QString &anchorId)
{
    if (!m_anchors.contains(anchorId)) {
        return false;
    }

    m_anchors.remove(anchorId);
    emit anchorUnregistered(anchorId);
    return true;
}

QQuick3DNode *SceneAnchorRegistry::anchorNode(const QString &anchorId) const
{
    auto it = m_anchors.find(anchorId);
    if (it != m_anchors.end() && !it.value().isNull()) {
        return it.value().data();
    }
    return nullptr;
}

bool SceneAnchorRegistry::hasAnchor(const QString &anchorId) const
{
    auto it = m_anchors.find(anchorId);
    return (it != m_anchors.end() && !it.value().isNull());
}

QStringList SceneAnchorRegistry::registeredAnchors() const
{
    QStringList result;
    for (auto it = m_anchors.constBegin(); it != m_anchors.constEnd(); ++it) {
        if (!it.value().isNull()) {
            result.append(it.key());
        }
    }
    return result;
}
