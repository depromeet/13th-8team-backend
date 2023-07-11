package com.dpm.pumping.workout.application

import com.dpm.pumping.crew.Crew
import com.dpm.pumping.user.domain.User
import com.dpm.pumping.user.domain.UserRepository
import com.dpm.pumping.workout.domain.entity.Workout
import com.dpm.pumping.workout.dto.WorkoutCreateDto
import com.dpm.pumping.workout.dto.WorkoutGetDto
import com.dpm.pumping.workout.repository.WorkoutRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class WorkoutService(
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository
){

    companion object {
        private const val DEFAULT_CREW_DURATION = 7L
    }

    @Transactional
    fun createWorkout(
        request: WorkoutCreateDto.Request, user: User
    ): WorkoutCreateDto.Response {
        val crew = user.currentCrew
            ?: throw IllegalArgumentException("아직 크루에 참여하지 않아 운동 기록을 저장할 수 없습니다.")

        val workout = Workout.of(user.uid!!, crew.crewId!!, request.timers)
        val created = workoutRepository.save(workout)
        return WorkoutCreateDto.Response(created.workoutId!!)
    }

    fun getWorkouts(userId: String?, loginUser: User): WorkoutGetDto.Response {
        val user = getUser(userId, loginUser)
        val crew = user.currentCrew
            ?: throw IllegalArgumentException("아직 크루에 참여하지 않아 운동 기록이 존재하지 않습니다.")

        val startDate = LocalDateTime.parse(crew.createDate).minusDays(1L)
        val endDate = startDate.plusDays(DEFAULT_CREW_DURATION).plusDays(1L)
        val workoutDatas = workoutRepository
            .findAllByCurrentCrewAndUserIdAndCreateDateBetween(crew.crewId!!, user.uid!!, startDate, endDate)

        val response = workoutDatas
            ?.map { workout -> getWorkoutByDay(workout, crew) }
            ?.toList()

        return WorkoutGetDto.Response(response)
    }

    private fun getUser(userId: String?, loginUser: User): User {
        return if (userId === null) {
            loginUser
        } else {
            userRepository.findById(userId)
                .orElseThrow { throw IllegalArgumentException("${userId}에 해당하는 유저를 찾을 수 없습니다.") }
        }
    }

    private fun getWorkoutByDay(workout: Workout, crew: Crew): WorkoutGetDto.WorkoutByDay {
        val maxWorkoutData = workout.getMaxWorkoutPart()
        val workoutCreatedAt = workout.createDate.toLocalDate()

        return WorkoutGetDto.WorkoutByDay(
            workoutDate = crew.calculateDays(workoutCreatedAt).toString(),
            totalTime = workout.getTotalTime(),
            averageHeartbeat = workout.getAverageHeartbeat(),
            totalCalories = workout.getTotalCalories(),
            maxWorkoutCategory = maxWorkoutData.first.name,
            maxWorkoutCategoryTime = maxWorkoutData.second
        )
    }
}
