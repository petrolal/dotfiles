#include "CameraRig.h"
#include <QDebug>
#include <cmath>

static float shortestAngleDifference(float from, float to)
{
    float diff = std::fmod(to - from + 180.0f, 360.0f);
    if (diff < 0.0f) {
        diff += 360.0f;
    }
    return diff - 180.0f;
}

CameraRig::CameraRig(QObject *parent)
    : QObject(parent)
{
    m_animator.setEasingCurve(QEasingCurve::InOutCubic);
    connect(&m_animator, &QVariantAnimation::valueChanged, this, &CameraRig::onAnimationValueChanged);
    connect(&m_animator, &QVariantAnimation::finished, this, &CameraRig::onAnimationFinished);
}

void CameraRig::setCamera(QQuick3DNode *cam)
{
    if (m_camera == cam) return;
    m_camera = cam;
    emit cameraChanged();
}

void CameraRig::setDuration(int ms)
{
    if (m_duration == ms || ms <= 0) return;
    m_duration = ms;
    emit durationChanged();
}

bool CameraRig::travelTo(QQuick3DNode *targetNode)
{
    if (!targetNode) {
        qWarning() << "[CameraRig] Cannot travel to null node.";
        return false;
    }
    return travelToTransform(targetNode->position(), targetNode->eulerRotation());
}

bool CameraRig::travelToTransform(const QVector3D &targetPos, const QVector3D &targetRot)
{
    if (!m_camera) {
        qWarning() << "[CameraRig] Camera is not set.";
        return false;
    }

    if (m_moving) {
        m_animator.stop();
    }

    m_startPos = m_camera->position();
    m_targetPos = targetPos;

    m_startRot = m_camera->eulerRotation();
    m_deltaRot = QVector3D(
        shortestAngleDifference(m_startRot.x(), targetRot.x()),
        shortestAngleDifference(m_startRot.y(), targetRot.y()),
        shortestAngleDifference(m_startRot.z(), targetRot.z())
    );

    m_animator.setDuration(m_duration);
    m_animator.setStartValue(0.0);
    m_animator.setEndValue(1.0);

    m_moving = true;
    emit movingChanged();
    emit travelStarted();

    m_animator.start();
    return true;
}

void CameraRig::stop()
{
    if (m_moving) {
        m_animator.stop();
        m_moving = false;
        emit movingChanged();
    }
}

void CameraRig::onAnimationValueChanged(const QVariant &value)
{
    if (!m_camera) return;

    qreal progress = value.toReal();
    emit travelProgress(progress);

    // Smooth position interpolation
    QVector3D curPos = m_startPos + (m_targetPos - m_startPos) * static_cast<float>(progress);
    m_camera->setPosition(curPos);

    // Smooth rotational interpolation with shortest path
    QVector3D curRot = m_startRot + m_deltaRot * static_cast<float>(progress);
    m_camera->setEulerRotation(curRot);
}

void CameraRig::onAnimationFinished()
{
    m_moving = false;
    emit movingChanged();

    if (m_camera) {
        m_camera->setPosition(m_targetPos);
        m_camera->setEulerRotation(m_startRot + m_deltaRot);
    }

    emit travelFinished();
}
