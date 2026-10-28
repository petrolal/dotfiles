#pragma once

#include <cmath>
#include <hyprutils/math/Mat3x3.hpp>
#include <hyprutils/math/Vector2D.hpp>

using namespace Hyprutils::Math;

class Transform {
  public:
	Mat3x3 m_mat;

	Transform(Vector2D pos = {0, 0}, Vector2D scale = {1, 1}, double rot = 0) {
		m_mat = Mat3x3::identity();
		m_mat.translate(pos);
		m_mat.scale(scale);
		m_mat.rotate(rot);
	}
	Transform(Mat3x3 mat) : m_mat(mat) {}

	// https://stackoverflow.com/questions/4361242/extract-rotation-scale-values-from-2d-transformation-matrix
	Vector2D getPos() const {
		const auto& mat = m_mat.getMatrix();
		return {mat[2], mat[5]};
	}
	Vector2D getScale() const {
		const auto& mat = m_mat.getMatrix();
		return {
			std::sqrt(mat[0] * mat[0] + mat[3] * mat[3]),
			std::sqrt(mat[1] * mat[1] + mat[4] * mat[4])};
	}
	float getRot() const {
		const auto& mat = m_mat.getMatrix();
		return std::atan2(-mat[1], mat[0]);
		// should be equivalent to
		// return std::atan2(mat[3], mat[4]);
	}

	Transform withPos(const Vector2D& pos) const {
		auto newMat = m_mat.getMatrix();
		newMat[2] = pos.x;
		newMat[5] = pos.y;
		return {newMat};
	}

	Transform inv() {
		auto m = [&](int y, int x) { return m_mat.getMatrix()[y * 3 + x]; };

		// computes the inverse of a matrix m
		double det = m(0, 0) * (m(1, 1) * m(2, 2) - m(2, 1) * m(1, 2)) -
			m(0, 1) * (m(1, 0) * m(2, 2) - m(1, 2) * m(2, 0)) +
			m(0, 2) * (m(1, 0) * m(2, 1) - m(1, 1) * m(2, 0));

		double invdet = 1 / det;

		std::array<float, 9> minv; // inverse of matrix m
		minv[0] = (m(1, 1) * m(2, 2) - m(2, 1) * m(1, 2)) * invdet;
		minv[1] = (m(0, 2) * m(2, 1) - m(0, 1) * m(2, 2)) * invdet;
		minv[2] = (m(0, 1) * m(1, 2) - m(0, 2) * m(1, 1)) * invdet;
		minv[3] = (m(1, 2) * m(2, 0) - m(1, 0) * m(2, 2)) * invdet;
		minv[4] = (m(0, 0) * m(2, 2) - m(0, 2) * m(2, 0)) * invdet;
		minv[5] = (m(1, 0) * m(0, 2) - m(0, 0) * m(1, 2)) * invdet;
		minv[6] = (m(1, 0) * m(2, 1) - m(2, 0) * m(1, 1)) * invdet;
		minv[7] = (m(2, 0) * m(0, 1) - m(0, 0) * m(2, 1)) * invdet;
		minv[8] = (m(0, 0) * m(1, 1) - m(1, 0) * m(0, 1)) * invdet;
		return {minv};
	}

	Transform translate(const Vector2D& offset) const { return {m_mat.copy().translate(offset)}; }
	Transform scale(const Vector2D& scale) const { return {m_mat.copy().scale(scale)}; }
	Transform scale(const float scale) const { return {m_mat.copy().scale(scale)}; }
	Transform rotate(float rot /* rad, CCW */) const { return {m_mat.copy().rotate(rot)}; }
	Transform transpose() const { return {m_mat.copy().transpose()}; }
	Transform transform(eTransform transform) const { return {m_mat.copy().transform(transform)}; }
	Transform multiply(const Transform& other) const { return {m_mat.copy().multiply(other.m_mat)}; }
	Vector2D multiply(const Vector2D& other) const {
		const auto m = m_mat.getMatrix();
		return {
			m[0] * other.x + m[1] * other.y + m[2],
			m[3] * other.x + m[4] * other.y + m[5]};
	}
	bool operator==(const Transform& other) const { return other.m_mat == m_mat; }

	std::string toString() const { return m_mat.toString(); }
	friend std::ostream& operator<<(std::ostream& os, const Transform& t) {
		os << t.toString();
		return os;
	}
};
