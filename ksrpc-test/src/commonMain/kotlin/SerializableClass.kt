import kotlinx.serialization.Serializable

@Serializable
data class ComplexClass(
    val intValue: Int?,
    val stringsValue: List<String?>?,
    val child: ComplexClass?,
    val otherValues: Map<String, OtherClass>
)

@Serializable
data class OtherClass(
    val bool: Boolean,
    val float: Float
)
