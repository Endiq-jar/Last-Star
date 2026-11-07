extends Area3D

@export var shard_index := 0
var spin_speed := 1.4
var float_phase := 0.0
var base_y := 0.0


func _process(delta: float) -> void:
	rotation.y += spin_speed * delta
	position.y = base_y + sin(Time.get_ticks_msec() * 0.0022 + float_phase) * 0.14
