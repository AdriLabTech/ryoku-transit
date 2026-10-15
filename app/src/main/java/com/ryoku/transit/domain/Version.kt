package com.ryoku.transit.domain

/** Versión semántica `mayor.menor.parche`. Acepta prefijo «v» y sufijo (`-beta`, `+n`), que se ignora al comparar. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val RE = Regex("""^v?(\d{1,4})\.(\d{1,4})\.(\d{1,4})(?:[-+].*)?$""")
        fun parse(s: String): Version? =
            RE.matchEntire(s.trim())?.destructured?.let { (a, b, c) -> Version(a.toInt(), b.toInt(), c.toInt()) }
    }
}
